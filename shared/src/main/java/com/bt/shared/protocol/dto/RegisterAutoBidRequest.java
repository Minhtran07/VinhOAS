package com.bt.shared.protocol.dto;

/**
 * Request đăng ký auto-bid cho 1 phiên.
 *
 * Server tự lấy bidderId từ session — client KHÔNG được phép đặt thay người khác.
 *
 * Quy tắc validate phía server:
 *  - maxBid phải > giá hiện tại
 *  - increment phải > 0
 *  - 1 bidder chỉ có 1 config active mỗi auction (UNIQUE bidder_id, auction_id)
 */
public class RegisterAutoBidRequest {

    private long auctionId;
    private double maxBid;
    private double increment;

    public RegisterAutoBidRequest() { /* Gson */ }

    public RegisterAutoBidRequest(long auctionId, double maxBid, double increment) {
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.increment = increment;
    }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public double getMaxBid() { return maxBid; }
    public void setMaxBid(double maxBid) { this.maxBid = maxBid; }

    public double getIncrement() { return increment; }
    public void setIncrement(double increment) { this.increment = increment; }
}
