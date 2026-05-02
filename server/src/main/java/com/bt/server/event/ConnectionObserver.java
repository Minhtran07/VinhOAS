package com.bt.server.event;

import com.bt.server.net.ClientConnection;
import com.bt.shared.event.AuctionFinishedDomainEvent;
import com.bt.shared.event.AuctionObserver;
import com.bt.shared.event.AuctionStartedDomainEvent;
import com.bt.shared.event.BidPlacedDomainEvent;
import com.bt.shared.protocol.Message;
import com.bt.shared.protocol.MessageCodec;
import com.bt.shared.protocol.MessageType;
import com.bt.shared.protocol.dto.AuctionFinishedEvent;
import com.bt.shared.protocol.dto.BidPlacedEvent;

import java.io.IOException;

/**
 * Bridge: nhận event domain (POJO), build wire-message, đẩy ra
 * {@link ClientConnection} để client nhận realtime.
 *
 * Một observer = một connection. Khi connection close, ta gọi
 * {@code AuctionEventBus.unsubscribeAll(this)} để dọn.
 *
 * equals/hashCode dựa trên connection id để 2 observer của cùng connection
 * không bị duplicate trong Set.
 */
public class ConnectionObserver implements AuctionObserver {

    private final ClientConnection connection;

    public ConnectionObserver(ClientConnection connection) {
        this.connection = connection;
    }

    public ClientConnection getConnection() {
        return connection;
    }

    @Override
    public void onBidPlaced(BidPlacedDomainEvent ev) {
        BidPlacedEvent dto = new BidPlacedEvent();
        dto.setAuctionId(ev.getAuctionId());
        dto.setBidderId(ev.getBidderId());
        dto.setBidderUsername(ev.getBidderUsername());
        dto.setAmount(ev.getAmount());
        dto.setBidTime(ev.getTimestamp());
        dto.setNewEndTime(ev.getNewEndTime());
        sendQuiet(MessageType.BID_PLACED_EVENT, dto);
    }

    @Override
    public void onAuctionFinished(AuctionFinishedDomainEvent ev) {
        AuctionFinishedEvent dto = new AuctionFinishedEvent();
        dto.setAuctionId(ev.getAuctionId());
        dto.setWinnerBidderId(ev.getWinnerBidderId());
        dto.setWinnerUsername(ev.getWinnerUsername());
        dto.setFinalPrice(ev.getFinalPrice());
        sendQuiet(MessageType.AUCTION_FINISHED_EVENT, dto);
    }

    @Override
    public void onAuctionStarted(AuctionStartedDomainEvent ev) {
        // Không có DTO riêng cho event này — gửi với payload tối thiểu
        // (Phase tiếp theo có thể thêm AuctionStartedEventDto nếu cần)
        Message msg = MessageCodec.build(MessageType.AUCTION_STARTED_EVENT, "",
                new long[] { ev.getAuctionId() });
        try {
            connection.sendMessage(msg);
        } catch (IOException ignored) { /* connection có thể đã đóng */ }
    }

    private void sendQuiet(MessageType type, Object payload) {
        if (connection.isClosed()) return;
        try {
            connection.sendMessage(MessageCodec.build(type, "", payload));
        } catch (IOException ex) {
            // Connection đã đóng từ phía client. Bus sẽ unsubscribe khi
            // RequestRouter cleanup, không cần xử lý thêm ở đây.
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionObserver)) return false;
        return connection.getConnectionId()
                == ((ConnectionObserver) o).connection.getConnectionId();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(connection.getConnectionId());
    }
}
