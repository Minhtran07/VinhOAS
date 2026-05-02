package com.bt.server.service;

import com.bt.server.dao.AuctionDAO;
import com.bt.server.dao.BidDAO;
import com.bt.server.dao.UserDAO;
import com.bt.server.event.AuctionEventBus;
import com.bt.shared.Auction;
import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.BidTransaction;
import com.bt.shared.User;
import com.bt.shared.event.AuctionFinishedDomainEvent;
import com.bt.shared.event.AuctionStartedDomainEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler chạy nền: mỗi giây quét DB tìm:
 *  - Phiên OPEN có start_time ≤ NOW() → chuyển RUNNING + push event Started.
 *  - Phiên RUNNING có end_time ≤ NOW() → chuyển FINISHED + push event Finished
 *    với winner do {@link WinnerStrategy} quyết định.
 *
 * Dùng {@link ScheduledExecutorService} thay vì Timer vì nó robust hơn:
 *  - Exception trong tick không kill scheduler
 *  - shutdown hỗ trợ chờ task đang chạy hoàn thành
 *
 * Strategy Pattern cho phép thay logic chọn winner mà không sửa scheduler.
 */
public class AuctionLifecycleScheduler {

    private static final long TICK_INTERVAL_SEC = 1;

    private final AuctionDAO auctionDAO;
    private final BidDAO bidDAO;
    private final UserDAO userDAO;
    private final AuctionEventBus eventBus;
    private final WinnerStrategy winnerStrategy;
    private final ScheduledExecutorService executor;

    public AuctionLifecycleScheduler(AuctionDAO auctionDAO, BidDAO bidDAO,
                                     UserDAO userDAO, AuctionEventBus eventBus,
                                     WinnerStrategy winnerStrategy) {
        this.auctionDAO = auctionDAO;
        this.bidDAO = bidDAO;
        this.userDAO = userDAO;
        this.eventBus = eventBus;
        this.winnerStrategy = winnerStrategy;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auction-lifecycle");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        executor.scheduleAtFixedRate(
                this::tickQuiet, 0, TICK_INTERVAL_SEC, TimeUnit.SECONDS);
        System.out.println("[Lifecycle] Scheduler started, tick="
                + TICK_INTERVAL_SEC + "s");
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Wrapper bắt mọi exception để scheduler không bị kill. */
    private void tickQuiet() {
        try {
            promoteOpenAuctions();
            finishExpiredAuctions();
        } catch (RuntimeException ex) {
            System.err.println("[Lifecycle] Tick error: " + ex.getMessage());
        }
    }

    /** OPEN có start_time đã đến → RUNNING. */
    private void promoteOpenAuctions() {
        List<Auction> openList = auctionDAO.findByStatus(AuctionStatus.OPEN);
        LocalDateTime now = LocalDateTime.now();
        for (Auction a : openList) {
            if (a.getStartTime() != null && !a.getStartTime().isAfter(now)) {
                if (auctionDAO.updateStatus(a.getId(), AuctionStatus.RUNNING, null)) {
                    System.out.println("[Lifecycle] Auction " + a.getId() + " → RUNNING");
                    eventBus.publish(new AuctionStartedDomainEvent(a.getId()));
                }
            }
        }
    }

    /** RUNNING có end_time đã qua → FINISHED + xác định winner. */
    private void finishExpiredAuctions() {
        List<Auction> expired = auctionDAO.findRunningExpired();
        for (Auction a : expired) {
            finishOne(a);
        }
    }

    private void finishOne(Auction a) {
        Long auctionId = a.getId();
        double startingPrice = a.getItem() != null
                ? a.getItem().getStartingPrice() : 0;

        // BƯỚC 1: chốt status FINISHED trong transaction IMMEDIATE — sau khi
        // commit, không còn bid mới nào có thể commit cho phiên này (bid sẽ
        // thấy status != RUNNING và bị reject ở BiddingTransactionDAO).
        // Tạm thời pass winnerId=null vì có thể có bid late chưa thấy ở đây.
        if (!auctionDAO.finishAuctionAtomic(auctionId, null)) {
            // Đã có thread khác finalize, hoặc auction không còn RUNNING — skip
            return;
        }

        // BƯỚC 2: chốt winner SAU khi status đã FINISHED — bid mới không thể
        // chen vào nữa. Highest đọc bây giờ là chân lý.
        Optional<BidTransaction> highest = bidDAO.findHighestByAuction(auctionId);
        Optional<BidTransaction> winnerBid =
                winnerStrategy.determineWinner(highest, startingPrice);
        Long winnerId = winnerBid.map(BidTransaction::getBidderId).orElse(null);
        double finalPrice = winnerBid.map(BidTransaction::getBidAmount).orElse(startingPrice);

        // BƯỚC 3: cập nhật winner_bidder_id (đã FINISHED, chỉ là metadata)
        if (winnerId != null) {
            auctionDAO.updateStatus(auctionId, AuctionStatus.FINISHED, winnerId);
        }

        String winnerName = winnerId == null ? null
                : userDAO.findById(winnerId).map(User::getUsername).orElse(null);
        System.out.println("[Lifecycle] Auction " + auctionId + " → FINISHED, winner="
                + (winnerName == null ? "(none)" : winnerName)
                + " @$" + finalPrice);
        eventBus.publish(new AuctionFinishedDomainEvent(
                auctionId, winnerId, winnerName, finalPrice));
    }
}
