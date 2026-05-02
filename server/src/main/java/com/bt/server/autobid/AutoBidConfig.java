package com.bt.server.autobid;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Cấu hình auto-bid của một bidder cho một phiên.
 *
 * Logic:
 *  - {@code maxBid}: giá tối đa bidder chấp nhận trả
 *  - {@code increment}: bước nhảy mỗi lần bot bid hộ
 *  - {@code registeredAt}: dùng để tie-break khi 2 user cùng maxBid
 *    (đăng ký trước thắng — tương tự eBay)
 */
public class AutoBidConfig {

    private final long bidderId;
    private final long auctionId;
    private final double maxBid;
    private final double increment;
    private final LocalDateTime registeredAt;
    private boolean active = true;

    public AutoBidConfig(long bidderId, long auctionId, double maxBid,
                         double increment, LocalDateTime registeredAt) {
        if (maxBid <= 0 || increment <= 0) {
            throw new IllegalArgumentException("maxBid và increment phải > 0");
        }
        this.bidderId = bidderId;
        this.auctionId = auctionId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registeredAt = registeredAt;
    }

    public long getBidderId() { return bidderId; }
    public long getAuctionId() { return auctionId; }
    public double getMaxBid() { return maxBid; }
    public double getIncrement() { return increment; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public boolean isActive() { return active; }
    public void deactivate() { this.active = false; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AutoBidConfig)) return false;
        AutoBidConfig c = (AutoBidConfig) o;
        return bidderId == c.bidderId && auctionId == c.auctionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bidderId, auctionId);
    }
}
