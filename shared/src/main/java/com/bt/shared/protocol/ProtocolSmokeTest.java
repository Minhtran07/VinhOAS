package com.bt.shared.protocol;

import com.bt.shared.UserRole;
import com.bt.shared.protocol.dto.AuctionDto;
import com.bt.shared.protocol.dto.BidPlacedEvent;
import com.bt.shared.protocol.dto.LoginRequest;
import com.bt.shared.protocol.dto.LoginResponse;
import com.bt.shared.protocol.dto.PlaceBidRequest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.LocalDateTime;

/**
 * Smoke test offline cho codec và DTO.
 *
 * Không cần server/DB — chỉ test:
 *  - Encode/decode 1 message round-trip
 *  - LocalDateTime adapter giữ nguyên giá trị
 *  - payloadAs trả đúng kiểu DTO
 *  - IO qua PipedStream (giả lập socket)
 */
public class ProtocolSmokeTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testEncodeDecodeRoundTrip();
        testLocalDateTimeRoundTrip();
        testPipedStreamFlow();
        testInvalidJson();

        System.out.println();
        System.out.println("===== " + passed + " passed, " + failed + " failed =====");
        if (failed > 0) System.exit(1);
    }

    private static void testEncodeDecodeRoundTrip() {
        LoginRequest payload = new LoginRequest("alice_s", "password");
        String reqId = Message.newRequestId();
        Message msg = MessageCodec.build(MessageType.LOGIN_REQUEST, reqId, payload);
        String json = MessageCodec.encode(msg);
        System.out.println("[1] Wire: " + json);

        Message back = MessageCodec.decode(json);
        check("type giữ nguyên", back.getType() == MessageType.LOGIN_REQUEST);
        check("requestId giữ nguyên", back.getRequestId().equals(reqId));

        LoginRequest payloadBack = MessageCodec.payloadAs(back, LoginRequest.class);
        check("username round-trip", payloadBack.getUsername().equals("alice_s"));
        check("password round-trip", payloadBack.getPassword().equals("password"));
    }

    private static void testLocalDateTimeRoundTrip() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 26, 14, 30, 15);
        BidPlacedEvent ev = new BidPlacedEvent();
        ev.setAuctionId(42);
        ev.setBidderId(7);
        ev.setBidderUsername("charlie_b");
        ev.setAmount(1500.0);
        ev.setBidTime(now);
        ev.setNewEndTime(now.plusMinutes(2));

        Message msg = MessageCodec.build(MessageType.BID_PLACED_EVENT, "", ev);
        String json = MessageCodec.encode(msg);
        System.out.println("[2] Wire: " + json);

        Message back = MessageCodec.decode(json);
        BidPlacedEvent evBack = MessageCodec.payloadAs(back, BidPlacedEvent.class);
        check("LocalDateTime bidTime round-trip", evBack.getBidTime().equals(now));
        check("LocalDateTime newEndTime round-trip",
                evBack.getNewEndTime().equals(now.plusMinutes(2)));
        check("amount giữ nguyên", evBack.getAmount() == 1500.0);
    }

    private static void testPipedStreamFlow() throws Exception {
        // Giả lập socket bằng pipe: cùng JVM, output → input
        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream(pos, 8192);

        BufferedWriter w = MessageCodec.writer(pos);
        BufferedReader r = MessageCodec.reader(pis);

        // Gửi 2 message, kiểm tra đọc lại đúng thứ tự
        Message m1 = MessageCodec.build(MessageType.LOGIN_REQUEST, "id-1",
                new LoginRequest("u1", "p1"));
        Message m2 = MessageCodec.build(MessageType.PLACE_BID_REQUEST, "id-2",
                new PlaceBidRequest(10, 20, 999.99));
        MessageCodec.writeMessage(w, m1);
        MessageCodec.writeMessage(w, m2);

        Message back1 = MessageCodec.readMessage(r);
        Message back2 = MessageCodec.readMessage(r);
        check("piped #1 type", back1.getType() == MessageType.LOGIN_REQUEST);
        check("piped #1 reqId", back1.getRequestId().equals("id-1"));
        check("piped #2 type", back2.getType() == MessageType.PLACE_BID_REQUEST);
        check("piped #2 amount",
                MessageCodec.payloadAs(back2, PlaceBidRequest.class).getAmount() == 999.99);
    }

    private static void testInvalidJson() {
        try {
            MessageCodec.decode("{ this is not valid json");
            fail("expected ProtocolException");
        } catch (ProtocolException ok) {
            pass("invalid JSON throws ProtocolException");
        }
        try {
            // payload sai DTO
            String wire = "{\"type\":\"LOGIN_REQUEST\",\"requestId\":\"x\","
                    + "\"payload\":{\"username\":\"u\",\"password\":\"p\"}}";
            Message m = MessageCodec.decode(wire);
            // Cố parse sang LoginResponse (sai DTO) — Gson tolerant nên không
            // throw, nhưng test verify không crash
            LoginResponse r = MessageCodec.payloadAs(m, LoginResponse.class);
            check("Gson tolerant với DTO khác (không null)", r != null);
            // Username thật trong LoginRequest sẽ không map được vào LoginResponse →
            // các field default
            check("LoginResponse mismatch không crash", r.getUserId() == 0);
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    // ----- helpers -----

    private static void check(String label, boolean cond) {
        if (cond) pass(label); else fail(label);
    }

    private static void pass(String label) {
        passed++;
        System.out.println("  ✓ " + label);
    }

    private static void fail(String label) {
        failed++;
        System.out.println("  ✗ " + label);
    }
}
