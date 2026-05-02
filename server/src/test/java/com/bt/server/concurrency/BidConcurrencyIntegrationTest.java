package com.bt.server.concurrency;

import com.bt.server.autobid.AutoBidConfig;
import com.bt.server.autobid.AutoBidEngine;
import com.bt.server.controller.AutoBidEngineHolder;
import com.bt.server.dao.AuctionDAO;
import com.bt.server.dao.BidDAO;
import com.bt.server.dao.DatabaseConnection;
import com.bt.server.dao.ItemDAO;
import com.bt.server.dao.UserDAO;
import com.bt.server.event.AuctionEventBus;
import com.bt.server.service.AuctionService;
import com.bt.shared.Auction;
import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.Bidder;
import com.bt.shared.BidTransaction;
import com.bt.shared.Electronics;
import com.bt.shared.Item;
import com.bt.shared.Seller;
import com.bt.shared.exception.AuctionException;
import com.bt.shared.protocol.dto.AuctionDto;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test cho concurrency của bidding engine.
 *
 * Mục tiêu chứng minh các invariant đã được fix:
 *  1. KHÔNG lost update: 50 thread bid lên cùng phiên, current_price cuối
 *     cùng = MAX của tất cả bid hợp lệ.
 *  2. MONOTONIC: current_price chỉ tăng, không bao giờ giảm.
 *  3. Đúng 1 winner: highest bid chính là winner, bidder của winner thật.
 *  4. Auto-bid race: 5 user auto-bid maxBid khác nhau → user maxBid cao nhất
 *     phải là winner cuối cùng.
 *  5. Phiên FINISHED thì không bid mới được thêm vào (scheduler race).
 *
 * Mỗi test dùng file SQLite riêng + reset DataSource để cô lập.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Bid Concurrency Integration Tests")
public class BidConcurrencyIntegrationTest {

    private static Path tmpDb;

    @BeforeAll
    static void setupSchema() throws Exception {
        // Đóng DataSource cũ nếu test khác đã init pool với URL khác (vd
        // application.properties trỏ MySQL không tồn tại trên máy CI).
        DatabaseConnection.shutdown();

        // Tạo file SQLite tạm rồi ép AppConfig dùng URL đó.
        tmpDb = Files.createTempFile("auction-test-", ".db");
        Files.deleteIfExists(tmpDb);
        String testUrl = "jdbc:sqlite:" + tmpDb.toAbsolutePath();
        System.setProperty("db.url", testUrl);
        forceAppConfigReload(testUrl);

        // Khởi DataSource mới → schema + migration tự apply
        try (java.sql.Connection ignore = DatabaseConnection.getConnection()) {
            // schema đã được initialize
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        DatabaseConnection.shutdown();
        if (tmpDb != null) Files.deleteIfExists(tmpDb);
        System.clearProperty("db.url");
    }

    // ================================================================
    // Test 1: 50 thread bid song song lên 1 phiên
    // ================================================================
    @Test
    @Order(1)
    @DisplayName("50 bidder song song: không lost update, đúng winner, giá monotonic")
    void test_50ConcurrentBidders_priceMonotonic_singleWinner() throws Exception {
        Fixture f = setupAuction(1_000_000);
        AuctionService service = f.service;

        int N = 50;
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);
        AtomicInteger ok = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        // Mỗi thread bid 1 lần với amount = 1_000_000 + i*100_000
        for (int i = 0; i < N; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    long bidderId = f.bidderIds.get(idx);
                    double amount = 1_000_000 + (idx + 1) * 100_000.0;
                    try {
                        service.placeBid(f.auctionId, bidderId, amount);
                        ok.incrementAndGet();
                    } catch (AuctionException ex) {
                        rejected.incrementAndGet();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Test timeout");
        pool.shutdown();

        // Invariant 1: tổng OK + rejected = N (không thread nào nuốt im lặng)
        assertEquals(N, ok.get() + rejected.get(),
                "Mọi bid phải có kết quả OK hoặc rejected");

        // Invariant 2: current_price = MAX của (startingPrice, mọi bid OK)
        Optional<AuctionDto> aOpt = service.getAuction(f.auctionId);
        assertTrue(aOpt.isPresent());
        AuctionDto a = aOpt.get();
        double expectedMax = 1_000_000;
        for (int i = 0; i < N; i++) {
            expectedMax = Math.max(expectedMax, 1_000_000 + (i + 1) * 100_000.0);
        }
        assertEquals(expectedMax, a.getCurrentPrice(), 0.01,
                "current_price phải bằng MAX bid (không lost update)");

        // Invariant 3: monotonic — đọc lịch sử, kiểm tra không có giá giảm
        // bất kỳ KHI ĐÃ TÍNH ĐẾN bid_amount tăng dần theo id (id tăng theo
        // commit order). Bid với amount thấp hơn current_price hiện tại sẽ
        // bị reject; vậy chuỗi bid OK PHẢI đơn điệu tăng theo id.
        List<BidTransaction> history = f.bidDAO.findByAuction(f.auctionId);
        double prev = 0;
        for (BidTransaction b : history) {
            assertTrue(b.getBidAmount() > prev,
                    "Bid #" + b.getId() + " amount=" + b.getBidAmount()
                            + " không > prev=" + prev + " — broken monotonicity");
            prev = b.getBidAmount();
        }

        // Invariant 4: đúng 1 winner = bidder có amount cao nhất
        Optional<BidTransaction> highest = f.bidDAO.findHighestByAuction(f.auctionId);
        assertTrue(highest.isPresent());
        assertEquals(expectedMax, highest.get().getBidAmount(), 0.01);
    }

    // ================================================================
    // Test 2: 5 user cùng auto-bid khác maxBid → user maxBid cao nhất thắng
    // ================================================================
    @Test
    @Order(2)
    @DisplayName("Auto-bid race: 5 user maxBid khác nhau, user maxBid cao nhất thắng")
    void test_autoBidRace_highestMaxBidWins() throws Exception {
        Fixture f = setupAuction(500_000);
        AuctionService service = f.service;

        // Tạo engine + holder cho test này
        AutoBidEngine engine = new AutoBidEngine(service);
        AutoBidEngineHolder.set(engine, f.eventBus);

        // 5 user với maxBid khác nhau, increment 100_000
        double[] maxBids = { 1_000_000, 2_000_000, 3_000_000, 5_000_000, 4_000_000 };
        long winnerExpectedId = -1;
        double winnerExpectedMax = 0;
        for (int i = 0; i < 5; i++) {
            long bidderId = f.bidderIds.get(i);
            AutoBidConfig cfg = new AutoBidConfig(bidderId, f.auctionId,
                    maxBids[i], 100_000, LocalDateTime.now());
            AutoBidEngineHolder.register(cfg, f.eventBus);
            if (maxBids[i] > winnerExpectedMax) {
                winnerExpectedMax = maxBids[i];
                winnerExpectedId = bidderId;
            }
        }

        // Trigger 1 bid manual khởi đầu chuỗi auto-bid
        long triggerBidder = f.bidderIds.get(5); // 1 user khác
        service.placeBid(f.auctionId, triggerBidder, 600_000);

        // Đợi auto-bid loop chạy xong (engine xử lý sync trong placeBid →
        // chuỗi đã hoàn thành khi placeBid trả về)
        Thread.sleep(1000); // buffer cho event bus dispatch

        // Winner phải là user có maxBid cao nhất (5M)
        Optional<BidTransaction> highest = f.bidDAO.findHighestByAuction(f.auctionId);
        assertTrue(highest.isPresent());
        assertEquals(winnerExpectedId, highest.get().getBidderId().longValue(),
                "Winner phải là user maxBid cao nhất (5M)");
        // Giá cuối phải <= maxBid của winner
        assertTrue(highest.get().getBidAmount() <= 5_000_000,
                "Giá thắng không vượt maxBid của winner");

        // Invariant: lịch sử bid monotonic
        List<BidTransaction> hist = f.bidDAO.findByAuction(f.auctionId);
        double prev = 0;
        for (BidTransaction b : hist) {
            assertTrue(b.getBidAmount() > prev,
                    "Auto-bid amount phải tăng đơn điệu");
            prev = b.getBidAmount();
        }
    }

    // ================================================================
    // Test 3: Phiên FINISHED không nhận bid mới
    // ================================================================
    @Test
    @Order(3)
    @DisplayName("Phiên FINISHED reject bid mới")
    void test_finishedAuction_rejectsNewBids() throws Exception {
        Fixture f = setupAuction(100_000);
        AuctionService service = f.service;

        // Bid 1 lần OK
        service.placeBid(f.auctionId, f.bidderIds.get(0), 200_000);

        // Force chuyển sang FINISHED
        f.auctionDAO.updateStatus(f.auctionId, AuctionStatus.FINISHED, f.bidderIds.get(0));

        // Mọi bid sau phải fail
        AuctionException ex = assertThrows(AuctionException.class,
                () -> service.placeBid(f.auctionId, f.bidderIds.get(1), 500_000),
                "Bid lên phiên FINISHED phải bị reject");
        assertTrue(ex.getMessage().toLowerCase().contains("finished")
                        || ex.getMessage().toLowerCase().contains("trạng thái"),
                "Error message nên chỉ rõ status: " + ex.getMessage());

        // current_price không được thay đổi sau khi reject
        AuctionDto a = service.getAuction(f.auctionId).get();
        assertEquals(200_000, a.getCurrentPrice(), 0.01);
    }

    // ================================================================
    // Test 4: Bid nhỏ hơn current_price phải bị reject
    // ================================================================
    @Test
    @Order(4)
    @DisplayName("Bid <= current_price bị reject (validate ở DB layer)")
    void test_bidBelowCurrent_rejected() throws Exception {
        Fixture f = setupAuction(100_000);
        AuctionService service = f.service;

        service.placeBid(f.auctionId, f.bidderIds.get(0), 500_000);

        // Bid với amount thấp hơn
        AuctionException ex = assertThrows(AuctionException.class,
                () -> service.placeBid(f.auctionId, f.bidderIds.get(1), 400_000));
        assertTrue(ex.getMessage().toLowerCase().contains("lớn hơn")
                        || ex.getMessage().toLowerCase().contains("greater"),
                "Error message phải nói về giá: " + ex.getMessage());
    }

    // ================================================================
    // Test 5: Bidder không thể tự bid item của chính mình (nếu là seller)
    // ================================================================
    @Test
    @Order(5)
    @DisplayName("Seller không thể bid sản phẩm của chính mình")
    void test_sellerCannotBidOwnItem() throws Exception {
        Fixture f = setupAuction(100_000);
        // Pass sellerId làm bidderId — AuctionService phải reject vì:
        //   (1) User này không phải role BIDDER, hoặc
        //   (2) sellerId == bidderId (validate ở DAO layer)
        // Cả 2 đều là behavior đúng — chỉ cần AuctionException được throw.
        AuctionException ex = assertThrows(AuctionException.class,
                () -> f.service.placeBid(f.auctionId, f.sellerId, 500_000));
        String msg = ex.getMessage().toLowerCase();
        assertTrue(
                msg.contains("bán") || msg.contains("seller")
                        || msg.contains("bidder") || msg.contains("role"),
                "Error message phải nói về role/seller, nhận: " + ex.getMessage());

        // Quan trọng nhất: bid này KHÔNG được lưu vào DB
        AuctionDto a = f.service.getAuction(f.auctionId).get();
        assertEquals(100_000, a.getCurrentPrice(), 0.01,
                "Seller bid đã bị reject — current_price không thay đổi");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static class Fixture {
        AuctionService service;
        AuctionEventBus eventBus;
        AuctionDAO auctionDAO;
        BidDAO bidDAO;
        long auctionId;
        long sellerId;
        List<Long> bidderIds = new ArrayList<>();
    }

    /** ID monotonic increment để username/email luôn unique giữa test
     *  (System.nanoTime đôi khi trả cùng value khi gọi nhanh). */
    private static final java.util.concurrent.atomic.AtomicInteger UNIQUE_ID =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Setup: tạo seller + N bidder + 1 auction RUNNING. */
    private Fixture setupAuction(double startingPrice) throws Exception {
        Fixture f = new Fixture();
        UserDAO userDAO = new UserDAO();
        ItemDAO itemDAO = new ItemDAO();
        f.auctionDAO = new AuctionDAO();
        f.bidDAO = new BidDAO();
        f.eventBus = AuctionEventBus.getInstance();
        f.service = new AuctionService(f.auctionDAO, itemDAO, f.bidDAO,
                userDAO, f.eventBus);

        // Tạo seller — username/email phải unique cho cả lifecycle JVM test.
        int sellerSuffix = UNIQUE_ID.incrementAndGet();
        Seller seller = new Seller();
        seller.setUsername("seller_t_" + sellerSuffix);
        seller.setEmail("seller_" + sellerSuffix + "@test.local");
        seller.setPassword("$2a$10$dummy.bcrypt.hash.placeholder.value.AAAAAAA");
        Optional<com.bt.shared.User> savedSeller = userDAO.register(seller);
        assertTrue(savedSeller.isPresent(),
                "Insert seller fail (username=" + seller.getUsername() + ")");
        f.sellerId = savedSeller.get().getId();
        seller = (Seller) savedSeller.get();

        // 60 bidder — username unique theo counter.
        for (int i = 0; i < 60; i++) {
            int bidderSuffix = UNIQUE_ID.incrementAndGet();
            Bidder b = new Bidder();
            b.setUsername("bidder_t_" + bidderSuffix);
            b.setEmail("bidder_" + bidderSuffix + "@test.local");
            b.setPassword("$2a$10$dummy.bcrypt.hash.placeholder.value.AAAAAAA");
            b.setAccountBalanceRaw(100_000_000.0);
            Optional<com.bt.shared.User> savedB = userDAO.register(b);
            assertTrue(savedB.isPresent(),
                    "Insert bidder #" + i + " fail (username=" + b.getUsername() + ")");
            f.bidderIds.add(savedB.get().getId());
        }

        // Tạo item + auction RUNNING
        Electronics item = new Electronics();
        item.setName("TestItem-" + System.nanoTime());
        item.setDescription("Concurrency test");
        item.setStartingPrice(startingPrice);
        item.setBrand("TestBrand");
        item.setWarrantyMonths(12);
        item.setSellerId(f.sellerId);
        Optional<Item> savedItem = itemDAO.insert(item);
        assertTrue(savedItem.isPresent());

        Auction auction = new Auction(savedItem.get(), seller,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1));
        auction.start();
        Optional<Auction> savedAuction = f.auctionDAO.insert(auction);
        assertTrue(savedAuction.isPresent());
        f.auctionId = savedAuction.get().getId();
        f.auctionDAO.updateStatus(f.auctionId, AuctionStatus.RUNNING, null);
        return f;
    }

    /**
     * AppConfig có static block load 1 lần. Để force re-resolve dbUrl từ
     * System property mới set, ta reset DataSource của DatabaseConnection.
     * Cách này hoạt động vì AppConfig.dbUrl() đọc System.getenv + properties
     * mỗi lần gọi, không cache.
     */
    private static void forceAppConfigReload(String testUrl) {
        try {
            Field f = Class.forName("com.bt.server.config.AppConfig")
                    .getDeclaredField("PROPS");
            f.setAccessible(true);
            java.util.Properties p = (java.util.Properties) f.get(null);
            p.setProperty("db.url", testUrl);
            p.setProperty("db.username", "");
            p.setProperty("db.password", "");
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Không override AppConfig được", ex);
        }
    }
}
