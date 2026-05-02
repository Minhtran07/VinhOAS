package com.bt.shared;

import com.bt.shared.exception.AuctionException;
import com.bt.shared.exception.AuctionStateException;
import com.bt.shared.exception.InvalidBidException;
import com.bt.shared.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Smoke test thủ công cho các invariant của module shared.
 *
 * Chạy: <code>mvn -pl shared exec:java -Dexec.mainClass=com.bt.shared.SharedSmokeTest</code>
 * hoặc compile và chạy trực tiếp.
 *
 * Không phải JUnit — chỉ để verify nhanh sau khi refactor. JUnit sẽ thêm
 * sau khi cấu trúc ổn định.
 */
public class SharedSmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testEntityEqualsByIdAndType();
        testUserValidation();
        testItemValidation();
        testItemFactory();
        testAuctionStateMachine();
        testAuctionPlaceBid();
        testAntiSnipingExtension();
        testBidHistoryUnmodifiable();

        System.out.println();
        System.out.println("===== RESULT: " + passed + " passed, " + failed + " failed =====");
        if (failed > 0) System.exit(1);
    }

    // ---------- Tests ----------

    private static void testEntityEqualsByIdAndType() {
        Bidder b1 = new Bidder("alice", "a@b.com", "pw", 100);
        Bidder b2 = new Bidder("bob", "b@c.com", "pw", 200);
        // Chưa persist → id null → khác nhau (theo username trong User.equals)
        check("two new bidders not equal", !b1.equals(b2));

        b1.setId(10L);
        b2.setId(10L);
        check("same id same type → equals", b1.equals(b2));

        Seller s = new Seller("alice", "a@b.com", "pw", 4.0);
        s.setId(10L);
        check("same id different type → not equals", !b1.equals(s));

        Set<User> set = new HashSet<>();
        set.add(b1);
        set.add(b2); // same id, same type
        check("HashSet với cùng id chỉ có 1 element", set.size() == 1);
    }

    private static void testUserValidation() {
        try {
            new Bidder("ab", "x@y.com", "pw", 0); // username < 3
            fail("expected IAE for short username");
        } catch (IllegalArgumentException ok) { pass("short username rejected"); }

        try {
            new Bidder("alice", "not-an-email", "pw", 0);
            fail("expected IAE for bad email");
        } catch (IllegalArgumentException ok) { pass("bad email rejected"); }

        try {
            new Bidder("alice", "a@b.com", "pw", -1);
            fail("expected IAE for negative balance");
        } catch (IllegalArgumentException ok) { pass("negative balance rejected"); }

        try {
            new Seller("alice", "a@b.com", "pw", 6.0);
            fail("expected IAE for rating > 5");
        } catch (IllegalArgumentException ok) { pass("rating>5 rejected"); }

        try {
            new Admin("alice", "a@b.com", "pw", 7);
            fail("expected IAE for accessLevel > 5");
        } catch (IllegalArgumentException ok) { pass("accessLevel>5 rejected"); }

        try {
            User.validateOrThrow("alice", "a@b.com", "pw");
            pass("validateOrThrow ok");
        } catch (ValidationException e) {
            fail("validateOrThrow should not throw: " + e.getMessage());
        }
    }

    private static void testItemValidation() {
        try {
            new Electronics("Laptop", "desc", -1, "Apple", 12);
            fail("expected IAE for negative price");
        } catch (IllegalArgumentException ok) { pass("negative price rejected"); }

        try {
            new Art("Painting", "desc", 100, "Picasso", 3000);
            fail("expected IAE for future year");
        } catch (IllegalArgumentException ok) { pass("future year rejected"); }

        try {
            new Vehicle("Car", "desc", 1000, "Toyota", "Camry", -5);
            fail("expected IAE for negative mileage");
        } catch (IllegalArgumentException ok) { pass("negative mileage rejected"); }

        Electronics e = new Electronics("iPhone", "new", 1000, "Apple", 12);
        check("category = ELECTRONICS", e.getCategory() == ItemCategory.ELECTRONICS);
    }

    private static void testItemFactory() {
        try {
            Map<String, Object> spec = new HashMap<>();
            spec.put(ItemFactory.KEY_BRAND, "Apple");
            spec.put(ItemFactory.KEY_WARRANTY_MONTHS, 12);
            Item it = ItemFactory.create(ItemCategory.ELECTRONICS,
                    "iPhone 15", "Like new", 1200.0, spec);
            check("factory tạo Electronics", it instanceof Electronics);
            check("factory set đúng startingPrice", it.getStartingPrice() == 1200.0);
        } catch (ValidationException e) {
            fail("factory should not throw: " + e.getMessage());
        }

        try {
            Map<String, Object> spec = new HashMap<>();
            // thiếu KEY_BRAND
            spec.put(ItemFactory.KEY_WARRANTY_MONTHS, 12);
            ItemFactory.create(ItemCategory.ELECTRONICS, "x", "y", 100, spec);
            fail("expected ValidationException for missing brand");
        } catch (ValidationException ok) {
            pass("factory phát hiện thiếu trường");
        }

        try {
            Map<String, Object> spec = new HashMap<>();
            spec.put(ItemFactory.KEY_ARTIST, "Picasso");
            spec.put(ItemFactory.KEY_YEAR_CREATED, "abc"); // sai kiểu
            ItemFactory.create(ItemCategory.ART, "x", "y", 100, spec);
            fail("expected ValidationException for bad int");
        } catch (ValidationException ok) {
            pass("factory phát hiện sai kiểu số");
        }
    }

    private static void testAuctionStateMachine() {
        Seller seller = new Seller("seller1", "s@s.com", "pw", 4.5);
        seller.setId(1L);
        Electronics it = new Electronics("Laptop", "desc", 500, "Dell", 12);

        Auction a = new Auction(it, seller,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        check("trạng thái ban đầu = OPEN", a.getStatus() == Auction.AuctionStatus.OPEN);

        try {
            a.markPaid(); // OPEN -> PAID không hợp lệ
            fail("expected AuctionStateException for OPEN→PAID");
        } catch (AuctionStateException ok) { pass("chặn OPEN→PAID"); }

        try {
            a.start();
            check("OPEN→RUNNING ok", a.getStatus() == Auction.AuctionStatus.RUNNING);
            a.finish();
            check("RUNNING→FINISHED ok", a.getStatus() == Auction.AuctionStatus.FINISHED);
            a.markPaid();
            check("FINISHED→PAID ok", a.getStatus() == Auction.AuctionStatus.PAID);
        } catch (AuctionStateException e) {
            fail("transitions hợp lệ không nên throw: " + e.getMessage());
        }

        try {
            a.cancel(); // PAID -> CANCELED không hợp lệ
            fail("expected AuctionStateException for PAID→CANCELED");
        } catch (AuctionStateException ok) { pass("chặn PAID→CANCELED"); }
    }

    private static void testAuctionPlaceBid() {
        Seller seller = new Seller("seller1", "s@s.com", "pw", 4.5);
        seller.setId(1L);
        Bidder b1 = new Bidder("alice", "a@b.com", "pw", 10000);
        b1.setId(2L);
        Bidder b2 = new Bidder("bob", "b@c.com", "pw", 10000);
        b2.setId(3L);
        Electronics it = new Electronics("Laptop", "desc", 500, "Dell", 12);

        Auction a = new Auction(it, seller,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));

        try {
            a.placeBid(b1, 600);
            fail("expected AuctionStateException khi chưa start");
        } catch (AuctionException ok) { pass("chặn bid khi OPEN"); }

        try {
            a.start();
        } catch (AuctionStateException e) {
            fail("start() should not throw: " + e.getMessage());
        }

        // bid <= startingPrice
        try {
            a.placeBid(b1, 500);
            fail("expected InvalidBidException khi bid = startingPrice");
        } catch (InvalidBidException ok) { pass("chặn bid = startingPrice");
        } catch (AuctionStateException e) { fail("không nên là state ex: " + e); }

        // bid hợp lệ
        try {
            BidTransaction tx = a.placeBid(b1, 600);
            check("bid 600 thành công", tx.getBidAmount() == 600);
            check("currentPrice = 600", a.getCurrentPrice() == 600);
        } catch (AuctionException e) { fail("bid hợp lệ throw: " + e.getMessage()); }

        // self-outbid
        try {
            a.placeBid(b1, 700);
            fail("expected InvalidBidException khi self-outbid");
        } catch (InvalidBidException ok) { pass("chặn self-outbid");
        } catch (AuctionStateException e) { fail("không nên state ex"); }

        // bidder khác
        try {
            a.placeBid(b2, 700);
            check("bid 700 từ b2 ok", a.getCurrentPrice() == 700);
            check("highest bidder = b2", a.getWinner().equals(b2));
        } catch (AuctionException e) { fail("bid b2 throw: " + e.getMessage()); }

        // seller bid sản phẩm của mình
        Bidder fakeSeller = new Bidder("seller1", "s2@s.com", "pw", 1000);
        fakeSeller.setId(1L); // cùng id với seller
        try {
            a.placeBid(fakeSeller, 800);
            fail("expected InvalidBidException khi seller tự bid");
        } catch (InvalidBidException ok) { pass("chặn seller tự bid");
        } catch (AuctionStateException e) { fail("không nên state ex"); }
    }

    private static void testAntiSnipingExtension() {
        Seller seller = new Seller("seller1", "s@s.com", "pw", 4.5);
        seller.setId(1L);
        Bidder bidder = new Bidder("alice", "a@b.com", "pw", 10000);
        bidder.setId(2L);
        Electronics it = new Electronics("Laptop", "desc", 500, "Dell", 12);

        // endTime cách 10s — nằm trong cửa sổ 30s
        LocalDateTime endTime = LocalDateTime.now().plusSeconds(10);
        Auction a = new Auction(it, seller, LocalDateTime.now(), endTime);
        try {
            a.start();
            a.placeBid(bidder, 600);
        } catch (AuctionException e) { fail("bid throw: " + e.getMessage()); return; }

        check("endTime đã được kéo dài thêm",
                a.getEndTime().isAfter(endTime));
    }

    private static void testBidHistoryUnmodifiable() {
        Seller seller = new Seller("seller1", "s@s.com", "pw", 4.5);
        seller.setId(1L);
        Bidder bidder = new Bidder("alice", "a@b.com", "pw", 10000);
        bidder.setId(2L);
        Auction a = new Auction(
                new Electronics("Laptop", "d", 100, "Dell", 12),
                seller, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        try {
            a.start();
            a.placeBid(bidder, 200);
        } catch (AuctionException e) { fail("bid throw"); return; }

        try {
            a.getBidHistory().clear();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ok) {
            pass("bidHistory là unmodifiable");
        }
    }

    // ---------- Helpers ----------

    private static void check(String label, boolean cond) {
        if (cond) pass(label); else fail(label);
    }

    private static void pass(String label) {
        passed++;
        System.out.println("  ✓ " + label);
    }

    private static void fail(String label) {
        failed++;
        System.out.println("  ✗ " + label);
    }
}
