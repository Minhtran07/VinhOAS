package com.bt.server.controller;

import com.bt.server.autobid.AutoBidConfig;
import com.bt.server.autobid.AutoBidEngine;
import com.bt.server.event.AuctionEventBus;

/**
 * Holder static để giữ AutoBidEngine khả dụng cho bất kỳ component nào cần
 * (vd: handler đăng ký auto-bid của RequestRouter — sẽ thêm khi có message
 * REGISTER_AUTOBID_REQUEST).
 *
 * Đây là một sự đánh đổi: thay vì dependency-injection đầy đủ (mỗi handler
 * nhận engine qua constructor), ta dùng holder để giữ scope phase đơn giản.
 * Có thể refactor sang DI khi project mở rộng.
 */
public final class AutoBidEngineHolder {

    private static AutoBidEngine engine;

    private AutoBidEngineHolder() {}

    public static void set(AutoBidEngine e, AuctionEventBus bus) {
        engine = e;
        // Engine đăng ký với bus như một observer toàn cục — sẽ nhận event
        // của mọi auction. Tuy nhiên bus của ta đang index theo auctionId,
        // nên engine cần subscribe khi user đăng ký auto-bid cho phiên cụ thể.
        // Sketch: ở đây chỉ giữ tham chiếu.
    }

    public static AutoBidEngine get() { return engine; }

    public static void register(AutoBidConfig config, AuctionEventBus bus) {
        if (engine == null) return;
        engine.register(config);
        bus.subscribe(config.getAuctionId(), engine);
    }
}
