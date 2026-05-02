package com.bt.shared.protocol.dto;

/**
 * Server broadcast cho mọi client đang kết nối khi seller tạo phiên mới.
 *
 * Mục đích: Bidder đang ở Dashboard sẽ thấy phiên mới xuất hiện ngay,
 * không cần bấm Refresh.
 *
 * Khác với BidPlacedEvent (cần subscribe theo auctionId), event này được
 * broadcast cho TẤT CẢ connection đang kết nối, vì client chưa biết
 * auctionId mới để mà subscribe.
 */
public class AuctionCreatedEvent {

    private AuctionDto auction;

    public AuctionCreatedEvent() { /* Gson */ }

    public AuctionCreatedEvent(AuctionDto auction) { this.auction = auction; }

    public AuctionDto getAuction() { return auction; }
    public void setAuction(AuctionDto auction) { this.auction = auction; }
}
