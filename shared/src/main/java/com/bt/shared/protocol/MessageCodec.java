package com.bt.shared.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Codec dùng Gson để encode/decode {@link Message} qua socket.
 *
 * Wire format: mỗi message là một dòng JSON kết thúc bằng {@code \n}.
 * Format này đơn giản, debug dễ (telnet/netcat đọc được), và chạy ổn định
 * với BufferedReader.readLine() ở phía nhận.
 *
 * Yêu cầu: payload JSON KHÔNG được chứa newline raw. Gson mặc định đã escape
 * newline trong String values, nên an toàn.
 *
 * Adapter cho {@link LocalDateTime} dùng ISO-8601, vì Gson mặc định không
 * biết serialize java.time.
 */
public final class MessageCodec {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Gson singleton — cấu hình một lần, dùng nhiều nơi. */
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (com.google.gson.JsonSerializer<LocalDateTime>)
                            (src, typeOfSrc, ctx) ->
                                    new com.google.gson.JsonPrimitive(src.format(ISO)))
            .registerTypeAdapter(LocalDateTime.class,
                    (com.google.gson.JsonDeserializer<LocalDateTime>)
                            (json, typeOfT, ctx) ->
                                    LocalDateTime.parse(json.getAsString(), ISO))
            .disableHtmlEscaping()
            .create();

    private MessageCodec() { /* utility */ }

    // ---------- Encode ----------

    public static String encode(Message msg) {
        return GSON.toJson(msg);
    }

    /** Build payload JsonElement từ DTO bất kỳ. */
    public static JsonElement toPayload(Object dto) {
        return GSON.toJsonTree(dto);
    }

    /** Build message hoàn chỉnh từ type + DTO + requestId. */
    public static Message build(MessageType type, String requestId, Object payload) {
        return new Message(type, requestId,
                payload == null ? null : toPayload(payload));
    }

    // ---------- Decode ----------

    public static Message decode(String json) throws ProtocolException {
        try {
            Message msg = GSON.fromJson(json, Message.class);
            if (msg == null || msg.getType() == null) {
                throw new ProtocolException("Message thiếu type: " + json);
            }
            return msg;
        } catch (JsonSyntaxException ex) {
            throw new ProtocolException("JSON sai cú pháp: " + ex.getMessage(), ex);
        }
    }

    /** Convert payload của Message sang DTO cụ thể. */
    public static <T> T payloadAs(Message msg, Class<T> clazz) throws ProtocolException {
        if (msg.getPayload() == null) {
            throw new ProtocolException("Payload null cho " + msg.getType());
        }
        try {
            return GSON.fromJson(msg.getPayload(), clazz);
        } catch (JsonSyntaxException ex) {
            throw new ProtocolException("Payload không khớp " + clazz.getSimpleName(), ex);
        }
    }

    /** Convert payload sang generic type (vd: List<AuctionDto>). */
    public static <T> T payloadAs(Message msg, Type type) throws ProtocolException {
        if (msg.getPayload() == null) {
            throw new ProtocolException("Payload null cho " + msg.getType());
        }
        try {
            return GSON.fromJson(msg.getPayload(), type);
        } catch (JsonSyntaxException ex) {
            throw new ProtocolException("Payload không khớp generic type", ex);
        }
    }

    // ---------- IO helpers ----------

    /**
     * Tạo BufferedReader bao InputStream với UTF-8.
     * Caller giữ trách nhiệm đóng socket; reader không cần đóng riêng.
     */
    public static BufferedReader reader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Tương tự cho writer — auto flush sau mỗi write line. */
    public static BufferedWriter writer(OutputStream out) {
        return new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    /**
     * Ghi 1 message vào writer dưới dạng 1 dòng JSON. Thread-safe nếu caller
     * tự đồng bộ (writer này không đồng bộ).
     */
    public static void writeMessage(BufferedWriter writer, Message msg) throws IOException {
        writer.write(encode(msg));
        writer.write("\n");
        writer.flush();
    }

    /** Đọc 1 message (1 dòng); trả null nếu stream đóng. */
    public static Message readMessage(BufferedReader reader)
            throws IOException, ProtocolException {
        String line = reader.readLine();
        if (line == null) return null;
        return decode(line);
    }
}
