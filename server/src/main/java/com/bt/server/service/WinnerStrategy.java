package com.bt.server.service;

import com.bt.shared.BidTransaction;

import java.util.Optional;

/**
 * Strategy Pattern cho việc xác định người thắng phiên đấu giá.
 *
 * Mặc định: bid cao nhất thắng (HighestBidStrategy). Có thể mở rộng:
 *  - SealedBidStrategy: phong thư, mọi người ghi giá, người cao nhất thắng
 *  - ReserveBidStrategy: phải vượt giá ngầm mới được thắng
 *
 * Tách interface để tầng controller chỉ cần inject strategy phù hợp,
 * không phải sửa AuctionLifecycleScheduler.
 */
public interface WinnerStrategy {

    /**
     * Xác định người thắng từ lịch sử bid và giá khởi điểm.
     *
     * @param highestBid bid cao nhất từ DB (có thể empty nếu chưa có bid)
     * @param startingPrice giá khởi điểm — dùng để fallback khi chưa có bid
     * @return Optional bid của người thắng; empty nếu không có winner (no bid)
     */
    Optional<BidTransaction> determineWinner(Optional<BidTransaction> highestBid,
                                             double startingPrice);
}
