package com.bt.server.dao;

import com.bt.shared.Art;
import com.bt.shared.Auction;
import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.BidTransaction;
import com.bt.shared.Bidder;
import com.bt.shared.Item;
import com.bt.shared.Seller;
import com.bt.shared.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Smoke test thủ công cho tầng DAO. Yêu cầu:
 *  - MySQL đang chạy ở local
 *  - Đã import schema từ database/auction_db.sql
 *  - application.properties có credentials đúng
 *
 * Cách chạy:
 *   mvn -pl server compile
 *   mvn -pl server exec:java -Dexec.mainClass=com.bt.server.dao.DaoSmokeTest
 * hoặc trong IDE.
 *
 * Test này SỬA dữ liệu trong DB (insert mới). Nếu muốn idempotent thì
 * có thể gói trong transaction rollback — bản này giữ đơn giản, in id
 * mới ra console để bạn kiểm tra.
 */
public class DaoSmokeTest {

    public static void main(String[] args) {
        try {
            testFindSeedUsers();
            testInsertItemAndAuctionAndBid();
            System.out.println("\n=== DAO smoke test PASSED ===");
        } catch (AssertionError e) {
            System.err.println("\n=== DAO smoke test FAILED: " + e.getMessage() + " ===");
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            DatabaseConnection.shutdown();
        }
    }

    private static void testFindSeedUsers() {
        UserDAO userDAO = new UserDAO();
        System.out.println("[1] List all users:");
        List<User> all = userDAO.findAll();
        all.forEach(u -> u.displayInfo());
        assertTrue(all.size() >= 6, "ít nhất 6 user trong seed");

        Optional<User> admin = userDAO.findByUsername("admin");
        assertTrue(admin.isPresent(), "tìm thấy admin");
        assertTrue(admin.get().getRole().name().equals("ADMIN"), "admin có role ADMIN");

        Optional<User> login = userDAO.findByUsername("alice_s");
        assertTrue(login.isPresent(), "tìm thấy alice_s qua findByUsername");
        assertTrue(login.get() instanceof Seller, "alice_s là Seller");
    }

    private static void testInsertItemAndAuctionAndBid() {
        UserDAO userDAO = new UserDAO();
        ItemDAO itemDAO = new ItemDAO();
        AuctionDAO auctionDAO = new AuctionDAO();
        BidDAO bidDAO = new BidDAO();

        Seller seller = (Seller) userDAO.findByUsername("alice_s")
                .orElseThrow(() -> new AssertionError("seed user alice_s missing"));
        Bidder bidder = (Bidder) userDAO.findByUsername("charlie_b")
                .orElseThrow(() -> new AssertionError("seed user charlie_b missing"));

        // 1. Insert một Item kiểu Art
        Art painting = new Art(
                "Test Painting " + System.currentTimeMillis(),
                "smoke test desc", 250.0, "TestArtist", 1900);
        painting.setSellerId(seller.getId());
        Item saved = itemDAO.insert(painting)
                .orElseThrow(() -> new AssertionError("insert item failed"));
        System.out.println("[2] Inserted item id=" + saved.getId());
        assertTrue(saved.getId() != null, "item có id");

        // Re-load để verify mapping
        Item loaded = itemDAO.findById(saved.getId())
                .orElseThrow(() -> new AssertionError("findById failed"));
        assertTrue(loaded instanceof Art, "load lại đúng kiểu Art");
        assertTrue(((Art) loaded).getYearCreated() == 1900, "yearCreated khớp");

        // 2. Insert một Auction
        Auction auction = new Auction(saved, seller,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1));
        Auction savedAuction = auctionDAO.insert(auction)
                .orElseThrow(() -> new AssertionError("insert auction failed"));
        System.out.println("[3] Inserted auction id=" + savedAuction.getId());

        boolean started = auctionDAO.updateStatus(savedAuction.getId(),
                AuctionStatus.RUNNING, null);
        assertTrue(started, "update status RUNNING ok");

        // 3. Insert một Bid
        BidTransaction bid = new BidTransaction(bidder, 300.0);
        bid.setAuctionId(savedAuction.getId());
        BidTransaction savedBid = bidDAO.insert(bid)
                .orElseThrow(() -> new AssertionError("insert bid failed"));
        System.out.println("[4] Inserted bid id=" + savedBid.getId());

        Optional<BidTransaction> highest = bidDAO.findHighestByAuction(savedAuction.getId());
        assertTrue(highest.isPresent(), "có highest bid");
        assertTrue(highest.get().getBidAmount() == 300.0, "highest = 300");

        int count = bidDAO.countByAuction(savedAuction.getId());
        assertTrue(count == 1, "đúng 1 bid trong DB cho phiên test");

        // 4. Cleanup nhẹ: cancel auction để dữ liệu test không lẫn vào prod
        auctionDAO.updateStatus(savedAuction.getId(), AuctionStatus.CANCELED, null);
        System.out.println("[5] Cleanup: marked auction CANCELED");
    }

    private static void assertTrue(boolean cond, String label) {
        if (!cond) throw new AssertionError(label);
        System.out.println("    ✓ " + label);
    }
}
