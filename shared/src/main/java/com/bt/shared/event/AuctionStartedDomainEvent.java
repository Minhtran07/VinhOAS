package com.bt.shared.event;

/** Phát ra khi phiên chuyển OPEN → RUNNING (do scheduler hoặc admin). */
public class AuctionStartedDomainEvent extends AuctionEvent {

    private static final long serialVersionUID = 1L;

    public AuctionStartedDomainEvent(long auctionId) {
        super(auctionId);
    }
}
