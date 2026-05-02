package com.bt.server.autobid;

import com.bt.server.event.AuctionEventBus;
import com.bt.server.service.AuctionService;
import com.bt.shared.event.AuctionObserver;
import com.bt.shared.event.BidPlacedDomainEvent;
import com.bt.shared.exception.AuctionException;
import com.bt.shared.protocol.dto.AuctionDto;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine xử lý auto-bid. Đăng ký vào {@link AuctionEventBus} như một observer
 * — sau mỗi {@code BidPlacedDomainEvent}, engine kiểm tra xem có config auto-bid
 * nào đang active có thể trả hộ không.
 *
 * Cấu trúc dữ liệu:
 *  - Map auctionId → PriorityQueue<AutoBidConfig> sắp theo (maxBid DESC, registeredAt ASC).
 *
 * Thuật toán đơn giản (đủ qua test):
 *  1. Sau mỗi bid (manual hoặc auto): lấy tất cả config active của phiên.
 *  2. Tìm config có maxBid cao nhất; nếu maxBid > giá hiện tại + increment → bid hộ
 *     với amount = currentPrice + increment.
 *  3. Skip nếu config thuộc về người vừa đặt bid (chính họ là dẫn đầu).
 *  4. Quay lại bước 1 (loop) cho tới khi không còn config nào đủ điều kiện.
 *
 * Race protection: gọi placeBid qua AuctionService — service đã có pessimistic
 * lock ở DAO, nên dù nhiều engine chạy song song, không lost update.
 */
public class AutoBidEngine implements AuctionObserver {

    private final AuctionService auctionService;
    private final ConcurrentHashMap<Long, PriorityQueue<AutoBidConfig>> queues =
            new ConcurrentHashMap<>();
    /**
     * Lock per-auction để serialize toàn bộ vòng auto-bid của 1 phiên.
     * Đảm bảo: không có 2 thread engine song song cùng đọc current_price
     * cũ rồi bid hộ với amount thấp hơn bid mới nhất → giá không bao giờ
     * giảm trong cùng 1 phiên.
     */
    private final ConcurrentHashMap<Long, Object> auctionLocks = new ConcurrentHashMap<>();

    /** Comparator: maxBid cao trước, registeredAt sớm trước. */
    private static final Comparator<AutoBidConfig> ORDER =
            Comparator.<AutoBidConfig>comparingDouble(c -> -c.getMaxBid())
                    .thenComparing(AutoBidConfig::getRegisteredAt);

    public AutoBidEngine(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    public void register(AutoBidConfig config) {
        queues.computeIfAbsent(config.getAuctionId(),
                        k -> new PriorityQueue<>(ORDER))
                .add(config);
    }

    public void unregister(long bidderId, long auctionId) {
        PriorityQueue<AutoBidConfig> q = queues.get(auctionId);
        if (q != null) {
            q.removeIf(c -> c.getBidderId() == bidderId);
        }
    }

    public List<AutoBidConfig> snapshotFor(long auctionId) {
        PriorityQueue<AutoBidConfig> q = queues.get(auctionId);
        if (q == null) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(q);
    }

    @Override
    public void onBidPlaced(BidPlacedDomainEvent event) {
        // Chống reentrancy quá sâu khi engine tự bid → tự nhận event của chính mình
        // Trong demo đơn giản: limit số vòng lặp để tránh infinite
        try {
            tryAutoBid(event.getAuctionId(), event.getBidderId(), 50);
        } catch (RuntimeException ex) {
            System.err.println("[AutoBid] Error: " + ex.getMessage());
        }
    }

    /**
     * Thử bid hộ. Loop cho tới khi không còn config nào đủ điều kiện hoặc
     * vượt số vòng lặp.
     *
     * Đọc current_price thực từ DB ở MỖI vòng (qua auctionService.getAuction),
     * KHÔNG dựa vào amount của event đầu vào — vì giữa lúc engine xử lý event
     * này có thể có nhiều bid khác đã commit. Dùng giá cũ → có thể bid hộ
     * với amount nhỏ hơn giá hiện tại → server reject (line BiddingTransactionDAO:92)
     * gây spam log; tệ hơn nữa nếu validate bị bỏ qua sẽ làm giá giảm.
     */
    private void tryAutoBid(long auctionId, long lastBidderId, int maxLoops) {
        PriorityQueue<AutoBidConfig> q = queues.get(auctionId);
        if (q == null || q.isEmpty()) return;

        // Serialize per-auction để 2 event đồng thời không gây bid xen kẽ ngược thứ tự
        Object lock = auctionLocks.computeIfAbsent(auctionId, k -> new Object());
        synchronized (lock) {
            for (int i = 0; i < maxLoops; i++) {
                // Đọc giá hiện tại MỚI NHẤT từ DB — không tin amount của event cũ
                Optional<AuctionDto> aOpt = auctionService.getAuction(auctionId);
                if (aOpt.isEmpty()) return;
                AuctionDto a = aOpt.get();
                if (a.getStatus() != com.bt.shared.Auction.AuctionStatus.RUNNING) return;
                double currentPrice = a.getCurrentPrice();

                // Người dẫn đầu hiện tại — không cần bid hộ thêm cho họ
                Long leaderId = a.getWinnerBidderId();
                long excludeId = leaderId != null ? leaderId : lastBidderId;

                AutoBidConfig top = peekActiveExcluding(q, excludeId);
                if (top == null) return;
                double next = currentPrice + top.getIncrement();
                if (next > top.getMaxBid()) {
                    // Người này hết đạn → loại khỏi queue tạm cho lần lặp này
                    q.remove(top);
                    top.deactivate();
                    continue;
                }
                if (next <= currentPrice) {
                    // Defensive: không bao giờ bid <= giá hiện tại
                    return;
                }
                try {
                    auctionService.placeBid(auctionId, top.getBidderId(), next);
                    lastBidderId = top.getBidderId();
                } catch (AuctionException ex) {
                    // Có thể là phiên đã đóng / amount không hợp lệ → dừng
                    return;
                }
            }
        }
    }

    /** Lấy config có maxBid cao nhất nhưng không phải bidder cuối cùng. */
    private AutoBidConfig peekActiveExcluding(PriorityQueue<AutoBidConfig> q, long excludeBidderId) {
        // PriorityQueue không hỗ trợ peek-with-filter; copy ra list, tìm.
        for (AutoBidConfig c : q) {
            if (c.isActive() && c.getBidderId() != excludeBidderId) return c;
        }
        return null;
    }
}
