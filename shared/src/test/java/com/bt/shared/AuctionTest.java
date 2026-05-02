package com.bt.shared;

import com.bt.shared.exception.AuctionStateException;
import com.bt.shared.exception.InvalidBidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho Auction state machine và placeBid rule.
 */
class AuctionTest {

    private Seller seller;
    private Bidder alice;
    private Bidder bob;
    private Auction auction;

    @BeforeEach
    void setUp() {
        seller = new Seller("seller1", "s@s.com", "pw", 4.5);
        seller.setId(1L);
        alice = new Bidder("alice", "a@a.com", "pw", 10000);
        alice.setId(2L);
        bob = new Bidder("bob", "b@b.com", "pw", 10000);
        bob.setId(3L);
        Electronics item = new Electronics("Laptop", "desc", 500, "Dell", 12);
        auction = new Auction(item, seller,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    }

    @Test
    @DisplayName("Trạng thái mặc định là OPEN")
    void initialStatusOpen() {
        assertEquals(Auction.AuctionStatus.OPEN, auction.getStatus());
    }

    @Test
    @DisplayName("OPEN → PAID không hợp lệ")
    void cannotJumpFromOpenToPaid() {
        assertThrows(AuctionStateException.class, auction::markPaid);
    }

    @Test
    @DisplayName("Quy trình OPEN → RUNNING → FINISHED → PAID hợp lệ")
    void happyPath() throws AuctionStateException {
        auction.start();
        assertEquals(Auction.AuctionStatus.RUNNING, auction.getStatus());
        auction.finish();
        assertEquals(Auction.AuctionStatus.FINISHED, auction.getStatus());
        auction.markPaid();
        assertEquals(Auction.AuctionStatus.PAID, auction.getStatus());
    }

    @Test
    @DisplayName("Bid khi OPEN bị reject")
    void cannotBidWhenOpen() {
        assertThrows(AuctionStateException.class,
                () -> auction.placeBid(alice, 600));
    }

    @Test
    @DisplayName("Bid <= startingPrice bị reject")
    void cannotBidAtOrBelowStartingPrice() throws Exception {
        auction.start();
        assertThrows(InvalidBidException.class, () -> auction.placeBid(alice, 500));
        assertThrows(InvalidBidException.class, () -> auction.placeBid(alice, 100));
    }

    @Test
    @DisplayName("Bid hợp lệ cập nhật currentPrice")
    void validBid() throws Exception {
        auction.start();
        BidTransaction tx = auction.placeBid(alice, 600);
        assertEquals(600.0, tx.getBidAmount());
        assertEquals(600.0, auction.getCurrentPrice());
    }

    @Test
    @DisplayName("Self-outbid bị reject")
    void selfOutbidRejected() throws Exception {
        auction.start();
        auction.placeBid(alice, 600);
        assertThrows(InvalidBidException.class, () -> auction.placeBid(alice, 700));
    }

    @Test
    @DisplayName("Seller không được tự bid sản phẩm của mình")
    void sellerCannotBidOwnAuction() throws Exception {
        auction.start();
        Bidder fakeSeller = new Bidder("seller1", "s2@s.com", "pw", 1000);
        fakeSeller.setId(1L); // Cùng id với seller
        assertThrows(InvalidBidException.class,
                () -> auction.placeBid(fakeSeller, 800));
    }

    @Test
    @DisplayName("Anti-sniping kéo dài endTime khi bid trong cửa sổ cuối")
    void antiSnipingExtension() throws Exception {
        LocalDateTime endTime = LocalDateTime.now().plusSeconds(10);
        Auction shortAuction = new Auction(
                new Electronics("Laptop", "d", 100, "Dell", 12),
                seller, LocalDateTime.now(), endTime);
        shortAuction.start();
        shortAuction.placeBid(alice, 200);
        assertTrue(shortAuction.getEndTime().isAfter(endTime),
                "endTime phải được kéo dài");
    }

    @Test
    @DisplayName("bidHistory unmodifiable từ ngoài")
    void bidHistoryIsUnmodifiable() throws Exception {
        auction.start();
        auction.placeBid(alice, 600);
        assertThrows(UnsupportedOperationException.class,
                () -> auction.getBidHistory().clear());
    }

    @Test
    @DisplayName("Winner = highest bidder sau nhiều bid")
    void winnerIsHighestBidder() throws Exception {
        auction.start();
        auction.placeBid(alice, 600);
        auction.placeBid(bob, 700);
        assertEquals(bob, auction.getWinner());
        assertEquals(700.0, auction.getCurrentPrice());
    }
}
