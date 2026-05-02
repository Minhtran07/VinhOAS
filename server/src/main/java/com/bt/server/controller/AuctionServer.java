package com.bt.server.controller;

import com.bt.server.autobid.AutoBidConfig;
import com.bt.server.autobid.AutoBidEngine;
import com.bt.server.config.AppConfig;
import com.bt.server.dao.AuctionDAO;
import com.bt.server.dao.AutoBidDAO;
import com.bt.server.dao.BidDAO;
import com.bt.server.dao.DatabaseConnection;
import com.bt.server.dao.ItemDAO;
import com.bt.server.dao.UserDAO;
import com.bt.server.event.AuctionEventBus;
import com.bt.server.event.ConnectionRegistry;
import com.bt.server.net.ClientConnection;
import com.bt.server.net.RequestRouter;
import com.bt.server.service.AuctionLifecycleScheduler;
import com.bt.server.service.AuctionService;
import com.bt.server.service.AuthService;
import com.bt.server.service.HighestBidStrategy;
import com.bt.server.service.PaymentService;
import com.bt.server.service.RatingService;
import com.bt.server.service.SellerService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bootstrap server. Wiring:
 *   DAO singletons → EventBus + ConnectionRegistry → Services → Router-per-connection.
 *
 * Mỗi client kết nối được gán 1 thread trong pool, thread đó chạy
 * {@link RequestRouter#serve()} cho tới khi client đóng.
 */
public class AuctionServer {

    public static void main(String[] args) {
        int port = AppConfig.serverPort();
        int maxClients = AppConfig.serverMaxClients();
        ExecutorService pool = Executors.newFixedThreadPool(maxClients);

        // FORCE init DB pool + schema TRƯỚC khi DAO/Service/Scheduler chạy.
        // Nhờ vậy log [Schema] xuất hiện ở thread main, dễ debug, và scheduler
        // tick lần đầu sẽ thấy schema đã sẵn sàng.
        try (java.sql.Connection ignored = DatabaseConnection.getConnection()) {
            System.out.println("[Server] DB pool + schema ready");
        } catch (java.sql.SQLException ex) {
            System.err.println("[Server] Lỗi init DB: " + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        UserDAO userDAO = new UserDAO();
        ItemDAO itemDAO = new ItemDAO();
        AuctionDAO auctionDAO = new AuctionDAO();
        BidDAO bidDAO = new BidDAO();
        AuctionEventBus eventBus = AuctionEventBus.getInstance();
        ConnectionRegistry registry = ConnectionRegistry.getInstance();

        AuthService authService = new AuthService(userDAO);
        AuctionService auctionService =
                new AuctionService(auctionDAO, itemDAO, bidDAO, userDAO, eventBus);
        SellerService sellerService =
                new SellerService(itemDAO, auctionDAO, userDAO, auctionService, registry, eventBus);
        PaymentService paymentService = new PaymentService(userDAO, registry);
        RatingService ratingService = new RatingService();

        AuctionLifecycleScheduler lifecycle = new AuctionLifecycleScheduler(
                auctionDAO, bidDAO, userDAO, eventBus, new HighestBidStrategy());
        lifecycle.start();

        AutoBidEngine autoBidEngine = new AutoBidEngine(auctionService);
        AutoBidEngineHolder.set(autoBidEngine, eventBus);

        // Restore auto-bid configs từ DB (sau khi server restart, các config
        // active vẫn còn trong DB nhưng chưa được nạp vào engine RAM).
        try {
            AutoBidDAO autoBidDAO = new AutoBidDAO();
            int restored = 0;
            for (AutoBidConfig cfg : autoBidDAO.findAllActive()) {
                AutoBidEngineHolder.register(cfg, eventBus);
                restored++;
            }
            System.out.println("[Server] Restored " + restored + " auto-bid configs");
        } catch (Exception ex) {
            System.err.println("[Server] Restore auto-bid fail: " + ex.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Server] Shutdown hook — closing pool, lifecycle, DB");
            lifecycle.shutdown();
            pool.shutdownNow();
            DatabaseConnection.shutdown();
        }));

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[Server] Listening on port " + port);

            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                try {
                    ClientConnection conn = new ClientConnection(socket);
                    System.out.println("[Server] New " + conn);
                    pool.submit(() -> {
                        RequestRouter router = new RequestRouter(
                                conn, authService, auctionService,
                                sellerService, paymentService, ratingService,
                                eventBus, registry);
                        router.serve();
                    });
                } catch (IOException ioe) {
                    System.err.println("[Server] Setup connection fail: " + ioe.getMessage());
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdownNow();
            DatabaseConnection.shutdown();
        }
    }
}
