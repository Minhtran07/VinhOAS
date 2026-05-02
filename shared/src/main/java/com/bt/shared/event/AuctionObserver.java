package com.bt.shared.event;

/**
 * Observer Pattern. Listener cho các event của một phiên đấu giá.
 *
 * Mặc định mỗi method có implement rỗng — observer chỉ override những
 * event mình quan tâm, không phải implement hết.
 *
 * Dùng default method thay vì abstract để nâng tính tiến hóa của API:
 * thêm event mới về sau không break observer cũ.
 */
public interface AuctionObserver {

    default void onBidPlaced(BidPlacedDomainEvent event) {}

    default void onAuctionStarted(AuctionStartedDomainEvent event) {}

    default void onAuctionFinished(AuctionFinishedDomainEvent event) {}
}
