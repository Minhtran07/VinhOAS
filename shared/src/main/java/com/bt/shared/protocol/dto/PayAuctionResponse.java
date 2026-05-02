package com.bt.shared.protocol.dto;

import java.time.LocalDateTime;

/**
 * Phản hồi sau khi thanh toán thành công. {@code newBalance} cho phép
 * client cập nhật ngay UI mà không cần query lại.
 */
public class PayAuctionResponse {

    private long auctionId;
    private double paidAmount;
    private double newBalance;
    private LocalDateTime paidAt;

    public PayAuctionResponse() { /* Gson */ }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }

    public double getNewBalance() { return newBalance; }
    public void setNewBalance(double newBalance) { this.newBalance = newBalance; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
