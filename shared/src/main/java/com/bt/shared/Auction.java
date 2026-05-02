package com.bt.shared;

import com.bt.shared.exception.AuctionStateException;
import com.bt.shared.exception.InvalidBidException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Phiên đấu giá: aggregate root của module shared.
 *
 * Vòng đời trạng thái (đúng theo đề bài):
 *   OPEN → RUNNING → FINISHED → PAID
 *                            └─▶ CANCELED
 *   OPEN → CANCELED (hủy trước khi chạy)
 *   RUNNING → CANCELED (hủy giữa chừng)
 *
 * Quy tắc đặt giá:
 *  - Chỉ chấp nhận khi trạng thái = RUNNING.
 *  - Bid mới phải > giá hiện tại (nếu chưa có bid, > startingPrice).
 *  - Bidder không được tự bid sản phẩm của chính mình (so theo sellerId).
 *  - Bidder không được "self-outbid" liên tục (đã là người dẫn đầu rồi
 *    thì không cần bid lại — tránh logic auto-bid bị lặp vô tận).
 *
 * Concurrency:
 *  - {@link #placeBid} dùng {@code synchronized} cấp object để hai luồng đặt
 *    giá đồng thời sẽ được serialize. Đây là biện pháp tối thiểu trong JVM;
 *    tầng DB cần dùng {@code SELECT ... FOR UPDATE} hoặc optimistic locking
 *    để chống race giữa nhiều JVM (xử lý ở DAO layer).
 *
 * Anti-sniping:
 *  - {@link #applyAntiSnipingIfNeeded} kéo dài endTime nếu bid xảy ra trong
 *    cửa sổ X giây cuối. Mặc định: trong 30s cuối → cộng thêm 60s.
 */
public class Auction extends Entity {

    private static final long serialVersionUID = 1L;

    /** Các trạng thái chính của phiên đấu giá. */
    public enum AuctionStatus {
        OPEN, RUNNING, FINISHED, PAID, CANCELED
    }

    /**
     * Bảng chuyển trạng thái hợp lệ. Khai báo tập trung để dễ kiểm tra,
     * mở rộng và viết unit test.
     */
    private static final Map<AuctionStatus, EnumSet<AuctionStatus>> ALLOWED_TRANSITIONS;
    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(AuctionStatus.class);
        ALLOWED_TRANSITIONS.put(AuctionStatus.OPEN,
                EnumSet.of(AuctionStatus.RUNNING, AuctionStatus.CANCELED));
        ALLOWED_TRANSITIONS.put(AuctionStatus.RUNNING,
                EnumSet.of(AuctionStatus.FINISHED, AuctionStatus.CANCELED));
        ALLOWED_TRANSITIONS.put(AuctionStatus.FINISHED,
                EnumSet.of(AuctionStatus.PAID, AuctionStatus.CANCELED));
        ALLOWED_TRANSITIONS.put(AuctionStatus.PAID, EnumSet.noneOf(AuctionStatus.class));
        ALLOWED_TRANSITIONS.put(AuctionStatus.CANCELED, EnumSet.noneOf(AuctionStatus.class));
    }

    /** Cửa sổ anti-sniping mặc định: bid trong 30s cuối → kéo dài 60s. */
    public static final Duration DEFAULT_ANTI_SNIPE_WINDOW = Duration.ofSeconds(30);
    public static final Duration DEFAULT_ANTI_SNIPE_EXTENSION = Duration.ofSeconds(60);

    private Item item;
    private Long sellerId;
    private transient Seller seller; // optional — không bắt buộc serialize cùng

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private final List<BidTransaction> bidHistory = new ArrayList<>();
    private BidTransaction highestBid;

    /**
     * Snapshot giá hiện tại lưu trong DB. DAO load và set vào đây để
     * {@link #getCurrentPrice()} trả đúng giá khi bid history chưa được load
     * (vd: sau khi restart server, hoặc khi list auctions). Khi {@code highestBid}
     * có giá trị, nó sẽ override snapshot này.
     */
    private Double currentPriceSnapshot;

    /** Constructor rỗng cho serialization / DAO. */
    public Auction() {
        super();
        this.status = AuctionStatus.OPEN;
    }

    public Auction(Item item, Seller seller, LocalDateTime startTime, LocalDateTime endTime) {
        super();
        if (item == null) {
            throw new IllegalArgumentException("Item không được null");
        }
        if (seller == null) {
            throw new IllegalArgumentException("Seller không được null");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime / endTime không được null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime phải sau startTime");
        }
        this.item = item;
        this.seller = seller;
        this.sellerId = seller.getId();
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AuctionStatus.OPEN;
    }

    // ---------- State transitions ----------

    /**
     * Chuyển trạng thái có kiểm tra tính hợp lệ. Throw nếu vi phạm
     * bảng {@link #ALLOWED_TRANSITIONS}.
     */
    public synchronized void transitionTo(AuctionStatus next) throws AuctionStateException {
        if (next == null) {
            throw new AuctionStateException("Trạng thái mới không được null");
        }
        if (status == next) return; // idempotent
        EnumSet<AuctionStatus> allowed = ALLOWED_TRANSITIONS.get(status);
        if (allowed == null || !allowed.contains(next)) {
            throw new AuctionStateException(
                    "Không thể chuyển trạng thái " + status + " → " + next);
        }
        this.status = next;
    }

    /** Bắt đầu phiên: OPEN → RUNNING. */
    public void start() throws AuctionStateException {
        transitionTo(AuctionStatus.RUNNING);
    }

    /** Kết thúc phiên: RUNNING → FINISHED. */
    public void finish() throws AuctionStateException {
        transitionTo(AuctionStatus.FINISHED);
    }

    /** Đánh dấu đã thanh toán: FINISHED → PAID. */
    public void markPaid() throws AuctionStateException {
        transitionTo(AuctionStatus.PAID);
    }

    /** Hủy phiên: từ OPEN/RUNNING/FINISHED → CANCELED. */
    public void cancel() throws AuctionStateException {
        transitionTo(AuctionStatus.CANCELED);
    }

    // ---------- Bidding ----------

    /**
     * Giá hiện tại. Ưu tiên: highestBid → snapshot từ DB → startingPrice.
     * Khi entity được load từ DAO, bid history thường không kèm theo, nên
     * DAO sẽ set {@link #currentPriceSnapshot} để giá hiện tại vẫn chính xác.
     */
    public synchronized double getCurrentPrice() {
        if (highestBid != null) return highestBid.getBidAmount();
        if (currentPriceSnapshot != null) return currentPriceSnapshot;
        return item != null ? item.getStartingPrice() : 0.0;
    }

    /** Set không validate — chỉ dùng khi load từ DB để snapshot giá hiện tại. */
    public synchronized void setCurrentPriceRaw(double price) {
        if (Double.isNaN(price) || price < 0) return;
        this.currentPriceSnapshot = price;
    }

    /**
     * Đặt giá. Validate đủ rule, chuyển động endTime nếu kích hoạt anti-sniping.
     *
     * @return BidTransaction vừa tạo
     * @throws InvalidBidException     nếu bid sai quy tắc
     * @throws AuctionStateException   nếu phiên không ở trạng thái RUNNING
     */
    public synchronized BidTransaction placeBid(Bidder bidder, double amount)
            throws InvalidBidException, AuctionStateException {
        if (bidder == null) {
            throw new InvalidBidException("Bidder không được null");
        }
        if (Double.isNaN(amount) || amount <= 0) {
            throw new InvalidBidException("Số tiền đặt giá phải > 0");
        }
        if (status != AuctionStatus.RUNNING) {
            throw new AuctionStateException(
                    "Không thể đặt giá khi phiên đang ở trạng thái " + status);
        }
        // Hết giờ → từ chối (controller tự gọi finish() sau).
        if (LocalDateTime.now().isAfter(endTime)) {
            throw new AuctionStateException("Phiên đấu giá đã kết thúc");
        }
        // Seller không tự bid sản phẩm của mình
        if (sellerId != null && bidder.getId() != null && sellerId.equals(bidder.getId())) {
            throw new InvalidBidException("Người bán không được đấu giá sản phẩm của chính mình");
        }
        // Không cho người đang dẫn đầu tự outbid (tránh loop auto-bid)
        if (highestBid != null && highestBid.getBidder() != null
                && bidder.equals(highestBid.getBidder())) {
            throw new InvalidBidException("Bạn đang là người dẫn đầu, không cần đặt thêm");
        }
        double current = getCurrentPrice();
        if (amount <= current) {
            throw new InvalidBidException(
                    "Bid phải lớn hơn giá hiện tại $" + current + ", nhận: $" + amount);
        }

        BidTransaction newBid = new BidTransaction(bidder, amount);
        newBid.setAuctionId(getId());
        bidHistory.add(newBid);
        highestBid = newBid;

        applyAntiSnipingIfNeeded(newBid.getTimestamp());
        return newBid;
    }

    /**
     * Nếu bid xảy ra trong cửa sổ {@link #DEFAULT_ANTI_SNIPE_WINDOW} giây cuối,
     * cộng thêm {@link #DEFAULT_ANTI_SNIPE_EXTENSION} giây vào endTime.
     */
    private void applyAntiSnipingIfNeeded(LocalDateTime bidTime) {
        if (bidTime == null || endTime == null) return;
        Duration remaining = Duration.between(bidTime, endTime);
        if (!remaining.isNegative()
                && remaining.compareTo(DEFAULT_ANTI_SNIPE_WINDOW) <= 0) {
            endTime = endTime.plus(DEFAULT_ANTI_SNIPE_EXTENSION);
        }
    }

    /** Người thắng cuộc, chỉ có ý nghĩa khi status = FINISHED/PAID. */
    public Bidder getWinner() {
        return highestBid != null ? highestBid.getBidder() : null;
    }

    // ---------- Getters / Setters ----------

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Seller getSeller() {
        return seller;
    }

    public void setSeller(Seller seller) {
        this.seller = seller;
        if (seller != null) {
            this.sellerId = seller.getId();
        }
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    /** Set trực tiếp status — chỉ dùng cho DAO khi load từ DB. */
    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    /** Lịch sử bid — read-only ra ngoài để tránh sửa từ caller. */
    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    public BidTransaction getHighestBid() {
        return highestBid;
    }

    @Override
    public void displayInfo() {
        System.out.println("=== AUCTION id=" + getId() + " | status=" + status + " ===");
        if (item != null) item.displayInfo();
        System.out.println("    sellerId=" + sellerId
                + " | start=" + startTime + " | end=" + endTime);
        if (highestBid != null) {
            System.out.println("    highest=$" + highestBid.getBidAmount()
                    + " by bidderId=" + highestBid.getBidderId());
        } else {
            System.out.println("    no bids yet, starting at $"
                    + (item != null ? item.getStartingPrice() : "?"));
        }
    }
}
