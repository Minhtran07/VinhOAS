package com.bt.server.service;

import com.bt.server.autobid.AutoBidConfig;
import com.bt.server.autobid.AutoBidEngine;
import com.bt.server.controller.AutoBidEngineHolder;
import com.bt.server.dao.AuctionDAO;
import com.bt.server.dao.AutoBidDAO;
import com.bt.server.dao.BidDAO;
import com.bt.server.dao.BidPersistenceResult;
import com.bt.server.dao.BiddingTransactionDAO;
import com.bt.server.dao.ItemDAO;
import com.bt.server.dao.UserDAO;
import com.bt.server.event.AuctionEventBus;
import com.bt.shared.Auction;
import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.BidTransaction;
import com.bt.shared.Item;
import com.bt.shared.User;
import com.bt.shared.event.AuctionFinishedDomainEvent;
import com.bt.shared.event.AuctionStartedDomainEvent;
import com.bt.shared.event.BidPlacedDomainEvent;
import com.bt.shared.exception.AuctionStateException;
import com.bt.shared.exception.InvalidBidException;
import com.bt.shared.exception.ValidationException;
import com.bt.shared.protocol.dto.AuctionDto;
import com.bt.shared.protocol.dto.BidDto;
import com.bt.shared.protocol.dto.PlaceBidResponse;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service xử lý nghiệp vụ liên quan đến auction.
 *
 * Phase 4: placeBid được delegate sang {@link BiddingTransactionDAO} —
 * chạy trọn trong 1 transaction với SELECT FOR UPDATE. Nhờ đó dù 100 thread
 * cùng bid lên 1 phiên, MySQL sẽ serialize, không lost update.
 *
 * Service không còn dùng synchronized — concurrency hoàn toàn dựa vào DB lock.
 */
public class AuctionService {

    private final AuctionDAO auctionDAO;
    private final ItemDAO itemDAO;
    private final BidDAO bidDAO;
    private final UserDAO userDAO;
    private final AuctionEventBus eventBus;
    private final BiddingTransactionDAO biddingTxn = new BiddingTransactionDAO();
    private final AutoBidDAO autoBidDAO = new AutoBidDAO();

    public AuctionService(AuctionDAO auctionDAO, ItemDAO itemDAO,
                          BidDAO bidDAO, UserDAO userDAO,
                          AuctionEventBus eventBus) {
        this.auctionDAO = auctionDAO;
        this.itemDAO = itemDAO;
        this.bidDAO = bidDAO;
        this.userDAO = userDAO;
        this.eventBus = eventBus;
    }

    // ---------- Read ----------

    public List<AuctionDto> listAuctions(AuctionStatus statusFilter) {
        List<Auction> list = (statusFilter == null)
                ? auctionDAO.findAll()
                : auctionDAO.findByStatus(statusFilter);
        List<AuctionDto> dtos = new ArrayList<>(list.size());
        for (Auction a : list) {
            dtos.add(toDto(a));
        }
        return dtos;
    }

    public Optional<AuctionDto> getAuction(long auctionId) {
        return auctionDAO.findById(auctionId).map(this::toDto);
    }

    public List<BidDto> getBidHistory(long auctionId) {
        List<BidTransaction> bids = bidDAO.findByAuction(auctionId);
        List<BidDto> dtos = new ArrayList<>(bids.size());
        for (BidTransaction b : bids) {
            BidDto d = new BidDto();
            d.setBidId(b.getId());
            d.setAuctionId(b.getAuctionId());
            d.setBidderId(b.getBidderId());
            d.setBidderUsername(usernameOf(b.getBidderId()));
            d.setAmount(b.getBidAmount());
            d.setBidTime(b.getTimestamp());
            d.setAutoBid(false);
            dtos.add(d);
        }
        return dtos;
    }

    // ---------- Write ----------

    /**
     * Đặt bid an toàn concurrency. Validate cơ bản trước khi vào DB,
     * rồi delegate transaction cho BiddingTransactionDAO.
     */
    public PlaceBidResponse placeBid(long auctionId, long bidderId, double amount)
            throws InvalidBidException, AuctionStateException, ValidationException {

        System.out.println("[Bid] Request: auction=" + auctionId
                + " bidder=" + bidderId + " amount=" + amount);

        // Validate bidder tồn tại + đúng role (lookup ngoài transaction để
        // không giữ DB lock lâu)
        Optional<User> userOpt = userDAO.findById(bidderId);
        if (userOpt.isEmpty()) {
            throw new ValidationException("Bidder không tồn tại: " + bidderId);
        }
        if (!(userOpt.get() instanceof com.bt.shared.Bidder)) {
            throw new ValidationException("User không phải role BIDDER");
        }
        String bidderUsername = userOpt.get().getUsername();

        // Pessimistic lock + insert + update — atomic
        BidPersistenceResult result;
        try {
            result = biddingTxn.placeBidAtomic(auctionId, bidderId, amount);
        } catch (SQLException e) {
            System.err.println("[Bid] DB error: " + e.getMessage());
            e.printStackTrace();
            throw new ValidationException("Lỗi DB khi đặt bid: " + e.getMessage());
        }
        System.out.println("[Bid] OK: bidId=" + result.getBidId()
                + " newPrice=" + result.getNewCurrentPrice());

        // Publish event ngoài transaction để tránh observer chặn DB lock
        eventBus.publish(new BidPlacedDomainEvent(
                auctionId, bidderId, bidderUsername,
                result.getNewCurrentPrice(),
                result.getNewEndTime()));

        PlaceBidResponse resp = new PlaceBidResponse();
        resp.setBidId(result.getBidId());
        resp.setAuctionId(auctionId);
        resp.setNewCurrentPrice(result.getNewCurrentPrice());
        resp.setNewEndTime(result.getNewEndTime());
        resp.setBidTime(java.time.LocalDateTime.now());
        return resp;
    }

    public void startAuction(long auctionId) throws AuctionStateException, ValidationException {
        Auction auction = auctionDAO.findById(auctionId)
                .orElseThrow(() -> new ValidationException("Auction không tồn tại"));
        auction.start();
        auctionDAO.updateStatus(auctionId, AuctionStatus.RUNNING, null);
        eventBus.publish(new AuctionStartedDomainEvent(auctionId));
    }

    /**
     * Đăng ký auto-bid cho 1 phiên. Validate:
     *  - phiên RUNNING
     *  - bidder không phải seller
     *  - maxBid > giá hiện tại
     *  - increment > 0
     *
     * Sau khi persist, register vào {@link AutoBidEngine} runtime để tham gia
     * vào loop bid hộ. Engine subscribe vào EventBus → mỗi bid mới sẽ trigger
     * tryAutoBid → kiểm tra config nào đủ điều kiện → bid hộ.
     *
     * @return AutoBidConfig đã lưu
     */
    public AutoBidConfig registerAutoBid(long auctionId, long bidderId,
                                          String bidderUsername,
                                          double maxBid, double increment)
            throws InvalidBidException, AuctionStateException, ValidationException {
        if (maxBid <= 0 || increment <= 0) {
            throw new InvalidBidException("maxBid và increment phải > 0");
        }
        Auction auction = auctionDAO.findById(auctionId)
                .orElseThrow(() -> new ValidationException("Phiên không tồn tại"));
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new AuctionStateException(
                    "Phiên đang ở " + auction.getStatus() + ", không cho đăng ký auto-bid");
        }
        if (auction.getSellerId() != null && auction.getSellerId() == bidderId) {
            throw new InvalidBidException(
                    "Người bán không được auto-bid sản phẩm của chính mình");
        }
        double currentPrice = auction.getCurrentPrice();
        if (maxBid <= currentPrice) {
            throw new InvalidBidException("maxBid phải lớn hơn giá hiện tại "
                    + currentPrice);
        }
        // Verify role
        Optional<User> userOpt = userDAO.findById(bidderId);
        if (userOpt.isEmpty() || !(userOpt.get() instanceof com.bt.shared.Bidder)) {
            throw new ValidationException("User không phải BIDDER hợp lệ");
        }

        AutoBidConfig cfg = autoBidDAO.upsert(bidderId, auctionId, maxBid, increment)
                .orElseThrow(() -> new ValidationException(
                        "Lỗi lưu config auto-bid vào DB"));

        // Register vào engine runtime + bus subscription
        AutoBidEngine engine = AutoBidEngineHolder.get();
        if (engine != null) {
            // Tránh duplicate config trong queue runtime: engine.unregister rồi register lại
            engine.unregister(bidderId, auctionId);
            AutoBidEngineHolder.register(cfg, eventBus);

            // Trigger ngay nếu user CHƯA dẫn đầu — bid hộ luôn 1 nhịp
            BidTransaction highest = bidDAO.findHighestByAuction(auctionId).orElse(null);
            boolean alreadyLeading = highest != null
                    && highest.getBidderId() != null
                    && highest.getBidderId() == bidderId;
            if (!alreadyLeading) {
                double next = currentPrice + increment;
                if (next <= maxBid) {
                    try {
                        placeBid(auctionId, bidderId, next);
                    } catch (Exception ex) {
                        // Không fatal — config đã lưu, lần bid manual của đối thủ
                        // sẽ trigger engine sau
                        System.err.println("[AutoBid] First bid attempt fail: "
                                + ex.getMessage());
                    }
                }
            }
        }
        return cfg;
    }

    public boolean cancelAutoBid(long auctionId, long bidderId) {
        AutoBidEngine engine = AutoBidEngineHolder.get();
        if (engine != null) engine.unregister(bidderId, auctionId);
        return autoBidDAO.deactivate(bidderId, auctionId);
    }

    public void finishAuction(long auctionId) throws AuctionStateException, ValidationException {
        Auction auction = auctionDAO.findById(auctionId)
                .orElseThrow(() -> new ValidationException("Auction không tồn tại"));
        auction.finish();

        BidTransaction highest = bidDAO.findHighestByAuction(auctionId).orElse(null);
        Long winnerId = (highest != null) ? highest.getBidderId() : null;
        String winnerName = usernameOf(winnerId);
        double finalPrice = (highest != null)
                ? highest.getBidAmount()
                : (auction.getItem() != null ? auction.getItem().getStartingPrice() : 0);

        auctionDAO.updateStatus(auctionId, AuctionStatus.FINISHED, winnerId);

        eventBus.publish(new AuctionFinishedDomainEvent(
                auctionId, winnerId, winnerName, finalPrice));
    }

    // ---------- Helpers ----------

    private AuctionDto toDto(Auction a) {
        AuctionDto d = new AuctionDto();
        d.setAuctionId(a.getId() == null ? 0 : a.getId());
        Item item = a.getItem();
        if (item != null) {
            d.setItemId(item.getId() == null ? 0 : item.getId());
            d.setItemName(item.getName());
            d.setItemDescription(item.getDescription());
            d.setItemCategory(item.getCategory());
            d.setStartingPrice(item.getStartingPrice());
        }
        d.setCurrentPrice(item != null ? a.getCurrentPrice() : 0);
        d.setSellerId(a.getSellerId() == null ? 0 : a.getSellerId());
        d.setSellerUsername(usernameOf(a.getSellerId()));
        d.setStartTime(a.getStartTime());
        d.setEndTime(a.getEndTime());
        d.setStatus(a.getStatus());
        d.setBidCount(a.getId() == null ? 0 : bidDAO.countByAuction(a.getId()));
        BidTransaction highest = bidDAO.findHighestByAuction(
                a.getId() == null ? 0 : a.getId()).orElse(null);
        if (highest != null) {
            d.setWinnerBidderId(highest.getBidderId());
            d.setWinnerUsername(usernameOf(highest.getBidderId()));
            d.setCurrentPrice(highest.getBidAmount());
        }
        return d;
    }

    private String usernameOf(Long userId) {
        if (userId == null) return null;
        return userDAO.findById(userId).map(User::getUsername).orElse(null);
    }
}
