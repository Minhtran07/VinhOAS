package com.bt.server.service;

import com.bt.shared.BidTransaction;

import java.util.Optional;

/**
 * Strategy mặc định: ai bid cao nhất là người thắng.
 *
 * Nếu chưa có bid nào → không có winner (Optional empty).
 */
public class HighestBidStrategy implements WinnerStrategy {

    @Override
    public Optional<BidTransaction> determineWinner(Optional<BidTransaction> highestBid,
                                                    double startingPrice) {
        return highestBid;
    }
}
