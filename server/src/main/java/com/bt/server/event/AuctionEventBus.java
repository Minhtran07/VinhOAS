package com.bt.server.event;

import com.bt.shared.event.AuctionEvent;
import com.bt.shared.event.AuctionFinishedDomainEvent;
import com.bt.shared.event.AuctionObserver;
import com.bt.shared.event.AuctionStartedDomainEvent;
import com.bt.shared.event.BidPlacedDomainEvent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bus phát event của các phiên đấu giá tới observer đăng ký.
 *
 * Subject (theo Observer Pattern) — giữ map auctionId → set observer.
 * Khi service gọi {@link #publish}, bus tự dispatch tới đúng nhóm observer
 * đang theo dõi phiên đó.
 *
 * Thread-safety: dùng {@link ConcurrentHashMap} + {@code newKeySet()} để
 * thread đặt bid và thread phát event không xung đột với thread subscribe.
 *
 * Singleton — một instance duy nhất trong server JVM.
 */
public class AuctionEventBus {

    private static volatile AuctionEventBus instance;

    /** auctionId → set observer đang theo dõi. */
    private final ConcurrentHashMap<Long, Set<AuctionObserver>> subscribers =
            new ConcurrentHashMap<>();

    private AuctionEventBus() {}

    public static AuctionEventBus getInstance() {
        if (instance == null) {
            synchronized (AuctionEventBus.class) {
                if (instance == null) instance = new AuctionEventBus();
            }
        }
        return instance;
    }

    public void subscribe(long auctionId, AuctionObserver observer) {
        subscribers.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
                .add(observer);
    }

    public void unsubscribe(long auctionId, AuctionObserver observer) {
        Set<AuctionObserver> set = subscribers.get(auctionId);
        if (set != null) {
            set.remove(observer);
            if (set.isEmpty()) subscribers.remove(auctionId);
        }
    }

    /** Hủy mọi subscription của 1 observer (khi connection đóng). */
    public void unsubscribeAll(AuctionObserver observer) {
        for (Set<AuctionObserver> set : subscribers.values()) {
            set.remove(observer);
        }
    }

    public int subscriberCount(long auctionId) {
        Set<AuctionObserver> set = subscribers.get(auctionId);
        return set == null ? 0 : set.size();
    }

    /**
     * Phát event tới mọi observer của phiên. Lỗi từ observer này không
     * chặn observer khác — observer bị isolate.
     */
    public void publish(AuctionEvent event) {
        Set<AuctionObserver> set = subscribers.get(event.getAuctionId());
        if (set == null || set.isEmpty()) return;
        for (AuctionObserver observer : set) {
            try {
                dispatch(observer, event);
            } catch (RuntimeException ex) {
                System.err.println("[EventBus] Observer error: " + ex.getMessage());
            }
        }
    }

    /** Map từng loại event cụ thể tới method phù hợp của observer. */
    private void dispatch(AuctionObserver observer, AuctionEvent event) {
        if (event instanceof BidPlacedDomainEvent) {
            observer.onBidPlaced((BidPlacedDomainEvent) event);
        } else if (event instanceof AuctionFinishedDomainEvent) {
            observer.onAuctionFinished((AuctionFinishedDomainEvent) event);
        } else if (event instanceof AuctionStartedDomainEvent) {
            observer.onAuctionStarted((AuctionStartedDomainEvent) event);
        }
        // Thêm loại mới: thêm 1 nhánh else if + method ở AuctionObserver
    }

    /** View read-only cho test/diagnostic. */
    public Set<Long> auctionIdsWithSubscribers() {
        return Collections.unmodifiableSet(subscribers.keySet());
    }
}
