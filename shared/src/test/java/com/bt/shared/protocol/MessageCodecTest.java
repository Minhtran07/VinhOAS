package com.bt.shared.protocol;

import com.bt.shared.protocol.dto.BidPlacedEvent;
import com.bt.shared.protocol.dto.LoginRequest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {

    @Test
    void roundTripLoginRequest() {
        LoginRequest payload = new LoginRequest("alice_s", "password");
        Message m = MessageCodec.build(MessageType.LOGIN_REQUEST, "req-1", payload);
        String json = MessageCodec.encode(m);

        Message back = MessageCodec.decode(json);
        assertEquals(MessageType.LOGIN_REQUEST, back.getType());
        assertEquals("req-1", back.getRequestId());

        LoginRequest p = MessageCodec.payloadAs(back, LoginRequest.class);
        assertEquals("alice_s", p.getUsername());
        assertEquals("password", p.getPassword());
    }

    @Test
    void localDateTimeRoundTrip() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 26, 14, 30, 15);
        BidPlacedEvent ev = new BidPlacedEvent();
        ev.setAuctionId(42);
        ev.setBidderId(7);
        ev.setBidderUsername("charlie_b");
        ev.setAmount(1500.0);
        ev.setBidTime(now);

        Message m = MessageCodec.build(MessageType.BID_PLACED_EVENT, "", ev);
        Message back = MessageCodec.decode(MessageCodec.encode(m));
        BidPlacedEvent evBack = MessageCodec.payloadAs(back, BidPlacedEvent.class);
        assertEquals(now, evBack.getBidTime());
        assertEquals(1500.0, evBack.getAmount());
    }

    @Test
    void invalidJsonThrows() {
        assertThrows(ProtocolException.class,
                () -> MessageCodec.decode("{ broken json"));
    }

    @Test
    void pipedStreamFlow() throws Exception {
        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream(pos, 8192);
        BufferedWriter w = MessageCodec.writer(pos);
        BufferedReader r = MessageCodec.reader(pis);

        Message m1 = MessageCodec.build(MessageType.LOGIN_REQUEST, "id-1",
                new LoginRequest("u1", "p1"));
        Message m2 = MessageCodec.build(MessageType.LOGIN_REQUEST, "id-2",
                new LoginRequest("u2", "p2"));
        MessageCodec.writeMessage(w, m1);
        MessageCodec.writeMessage(w, m2);

        Message back1 = MessageCodec.readMessage(r);
        Message back2 = MessageCodec.readMessage(r);
        assertEquals("id-1", back1.getRequestId());
        assertEquals("id-2", back2.getRequestId());
    }
}
