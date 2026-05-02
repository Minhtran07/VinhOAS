package com.bt.server.service;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import com.bt.shared.Auction;

/**
 * Singleton quản lý tập các phiên đấu giá đang chạy trong JVM của server.
 *
 * Dùng {@link ConcurrentHashMap} để truy cập nhiều thread an toàn — cần thiết
 * khi mỗi client kết nối được xử lý bằng một thread riêng.
 */
public class AuctionManager {

    private static volatile AuctionManager instance;

    /** Key = auction id (Long do DB cấp). */
    private final ConcurrentHashMap<Long, Auction> auctions = new ConcurrentHashMap<>();

    private AuctionManager() {
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    public void addAuction(Auction auction) {
        if (auction == null || auction.getId() == null) {
            throw new IllegalArgumentException("Auction phải có id trước khi đăng ký");
        }
        auctions.put(auction.getId(), auction);
    }

    public Auction getAuction(Long auctionId) {
        return auctionId == null ? null : auctions.get(auctionId);
    }

    public Collection<Auction> listAll() {
        return Collections.unmodifiableCollection(auctions.values());
    }

    public void remove(Long auctionId) {
        if (auctionId != null) auctions.remove(auctionId);
    }
}

