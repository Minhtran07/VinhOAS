package com.bt.shared.event;

/** Phát ra khi phiên đấu giá kết thúc (hết giờ hoặc bị finish thủ công). */
public class AuctionFinishedDomainEvent extends AuctionEvent {

    private static final long serialVersionUID = 1L;

    private final Long winnerBidderId;
    private final String winnerUsername;
    private final double finalPrice;

    public AuctionFinishedDomainEvent(long auctionId, Long winnerBidderId,
                                      String winnerUsername, double finalPrice) {
        super(auctionId);
        this.winnerBidderId = winnerBidderId;
        this.winnerUsername = winnerUsername;
        this.finalPrice = finalPrice;
    }

    public Long getWinnerBidderId() { return winnerBidderId; }
    public String getWinnerUsername() { return winnerUsername; }
    public double getFinalPrice() { return finalPrice; }
}
