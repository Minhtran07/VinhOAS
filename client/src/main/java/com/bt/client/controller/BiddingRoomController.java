package com.bt.client.controller;

import com.bt.client.net.AuctionClient;
import com.bt.client.net.AuctionClientException;
import com.bt.client.net.ServerConnection;
import com.bt.client.session.Session;
import com.bt.client.ui.SceneManager;
import com.bt.client.util.Dialogs;
import com.bt.client.util.ErrorMessages;
import com.bt.client.util.MoneyFormat;
import com.bt.client.util.MoneyTextField;
import com.bt.shared.protocol.Message;
import com.bt.shared.protocol.MessageCodec;
import com.bt.shared.protocol.MessageType;
import com.bt.shared.protocol.dto.AuctionDto;
import com.bt.shared.protocol.dto.AuctionFinishedEvent;
import com.bt.shared.protocol.dto.BidDto;
import com.bt.shared.protocol.dto.BidPlacedEvent;
import com.bt.shared.protocol.dto.PayAuctionResponse;
import com.bt.shared.protocol.dto.RateSellerResponse;
import com.bt.shared.protocol.dto.RegisterAutoBidResponse;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phòng đấu giá realtime cho 1 phiên cụ thể.
 *
 * Tích hợp Phase 3 (subscribe + push event) và Phase 8 (line chart):
 *  - initialize(): load auction info + bid history + subscribe event
 *  - Listener bắt BID_PLACED_EVENT, AUCTION_FINISHED_EVENT — cập nhật UI
 *    qua Platform.runLater
 *  - Khi rời phòng: unsubscribe + remove listener
 */
public class BiddingRoomController {

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Cách chuyển auctionId từ Dashboard sang khi switch scene. */
    private static long activeAuctionId;
    public static void setActiveAuctionId(long id) { activeAuctionId = id; }

    @FXML private Label itemNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label countdownLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label leaderLabel;
    @FXML private Label endTimeLabel;
    @FXML private Label bidErrorLabel;
    @FXML private TextField bidInput;
    @FXML private ListView<String> bidHistoryList;
    @FXML private LineChart<Number, Number> priceChart;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private javafx.scene.control.Button autoBidBtn;
    @FXML private Label autoBidStatusLabel;

    private final AuctionClient client = new AuctionClient();
    private final ObservableList<String> historyData = FXCollections.observableArrayList();
    private XYChart.Series<Number, Number> series;

    private long auctionId;
    private LocalDateTime auctionStart;
    private LocalDateTime auctionEnd;
    private double startingPrice;
    private int bidIndex; // Số bid đã add vào chart (cho trục X)
    private Consumer<Message> eventListener;
    private Timeline countdownTimer;

    @FXML
    public void initialize() {
        System.out.println("[BiddingRoom] initialize start, auctionId=" + activeAuctionId);
        try {
            auctionId = activeAuctionId;
            // In trạng thái các @FXML field — debug khi initialize fail im lặng
            System.out.println("[BiddingRoom] FXML fields: "
                    + "bidHistoryList=" + (bidHistoryList != null)
                    + " bidInput=" + (bidInput != null)
                    + " priceChart=" + (priceChart != null)
                    + " xAxis=" + (xAxis != null)
                    + " yAxis=" + (yAxis != null)
                    + " currentPriceLabel=" + (currentPriceLabel != null));
            if (bidHistoryList != null) {
                bidHistoryList.setItems(historyData);
            }
            System.out.println("[BiddingRoom] step1: bidHistoryList OK");

            if (bidInput != null) {
                MoneyTextField.install(bidInput);
                bidInput.setOnAction(e -> handleBid());
            }
            System.out.println("[BiddingRoom] step2: bidInput OK");

            series = new XYChart.Series<>();
            series.setName("Giá đấu");
            // Phòng trường hợp priceChart hoặc các axes bị null do FXML inject lỗi:
            // log rõ và bỏ qua các thao tác null-unsafe để không phá initialize().
            if (priceChart != null) {
                priceChart.getData().add(series);
                priceChart.setLegendVisible(false);
                priceChart.setAnimated(false);
                priceChart.setCreateSymbols(true);
            } else {
                System.err.println("[BiddingRoom] WARN priceChart=null — chart sẽ không hoạt động");
            }
            // Trục X chuyển sang "thứ tự bid" — autorange theo số bid
            if (xAxis != null) {
                xAxis.setAutoRanging(true);
                xAxis.setForceZeroInRange(true);
                xAxis.setLabel("Thứ tự bid");
            } else {
                System.err.println("[BiddingRoom] WARN xAxis=null");
            }
            if (yAxis != null) {
                yAxis.setAutoRanging(true);
                yAxis.setForceZeroInRange(false);
                yAxis.setLabel("Giá (VND)");
            } else {
                System.err.println("[BiddingRoom] WARN yAxis=null");
            }
            System.out.println("[BiddingRoom] step3: priceChart OK");

            eventListener = this::onServerEvent;
            ServerConnection.getInstance().addEventListener(eventListener);
            System.out.println("[BiddingRoom] step4: eventListener OK");

            new Thread(this::loadAndSubscribe, "bidding-init").start();
            System.out.println("[BiddingRoom] step5: load thread started");

            countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1),
                    e -> updateCountdown()));
            countdownTimer.setCycleCount(Timeline.INDEFINITE);
            countdownTimer.play();
            System.out.println("[BiddingRoom] step6: countdown timer OK");
        } catch (RuntimeException ex) {
            System.err.println("[BiddingRoom] initialize FAIL:");
            ex.printStackTrace();
            throw ex;
        }
    }

    private void loadAndSubscribe() {
        System.out.println("[BiddingRoom] load auctionId=" + auctionId);
        try {
            AuctionDto a = client.getAuction(auctionId);
            List<BidDto> history = client.getBidHistory(auctionId);
            client.subscribe(auctionId);
            System.out.println("[BiddingRoom] loaded: " + a.getItemName()
                    + " currentPrice=" + a.getCurrentPrice()
                    + " status=" + a.getStatus()
                    + " historySize=" + history.size());
            Platform.runLater(() -> applyAuction(a, history));
        } catch (AuctionClientException ex) {
            System.err.println("[BiddingRoom] load FAIL: " + ex.getMessage());
            ex.printStackTrace();
            Platform.runLater(() -> {
                // Không silently — set label rõ ràng để user biết
                if (itemNameLabel != null) itemNameLabel.setText("(Lỗi tải phiên)");
                if (bidErrorLabel != null) bidErrorLabel.setText(
                        "Không tải được phiên: " + ex.getMessage()
                                + "  — Kiểm tra server đã chạy chưa?");
                showError(ex);
            });
        }
    }

    private void applyAuction(AuctionDto a, List<BidDto> history) {
        System.out.println("[BiddingRoom] applyAuction start");
        try {
            if (itemNameLabel != null) {
                itemNameLabel.setText(a.getItemName() == null ? "(không tên)" : a.getItemName());
            }
            applyStatusBadge(String.valueOf(a.getStatus()));
            if (currentPriceLabel != null) {
                currentPriceLabel.setText(MoneyFormat.vnd(a.getCurrentPrice()));
            }
            if (leaderLabel != null) {
                leaderLabel.setText(a.getWinnerUsername() != null
                        ? "Dẫn đầu: " + a.getWinnerUsername()
                        : "Chưa có ai đặt giá");
            }
            auctionStart = a.getStartTime();
            auctionEnd = a.getEndTime();
            startingPrice = a.getStartingPrice();
            if (endTimeLabel != null) {
                endTimeLabel.setText("Kết thúc: "
                        + (auctionEnd == null ? "-" : auctionEnd.format(HMS)));
            }
            System.out.println("[BiddingRoom] applyAuction: labels OK");

            // Disable input nếu user không phải BIDDER hoặc phiên không RUNNING
            com.bt.shared.UserRole role = Session.get().getRole();
            boolean isBidder = role == com.bt.shared.UserRole.BIDDER;
            boolean isRunning = a.getStatus() != null
                    && a.getStatus() == com.bt.shared.Auction.AuctionStatus.RUNNING;
            if (!isBidder) {
                if (bidInput != null) bidInput.setDisable(true);
                if (autoBidBtn != null) autoBidBtn.setDisable(true);
                if (bidErrorLabel != null) bidErrorLabel.setText(
                        "Chỉ tài khoản BIDDER mới được đặt giá. Bạn đang xem ở chế độ chỉ đọc.");
            } else if (!isRunning) {
                if (bidInput != null) bidInput.setDisable(true);
                if (autoBidBtn != null) autoBidBtn.setDisable(true);
                if (bidErrorLabel != null) bidErrorLabel.setText(
                        "Phiên đang ở trạng thái " + a.getStatus() + " — không thể đặt giá.");
            } else {
                if (bidInput != null) bidInput.setDisable(false);
                if (autoBidBtn != null) autoBidBtn.setDisable(false);
                if (bidErrorLabel != null) bidErrorLabel.setText("");
            }
            System.out.println("[BiddingRoom] applyAuction: input state OK");

            // Init chart: điểm gốc tại bid #0 = startingPrice → baseline.
            // Đảm bảo chart MONOTONIC: nếu DB có bid out-of-order vì
            // race cũ, chỉ vẽ điểm khi amount tăng so với điểm cuối — lịch
            // sử text vẫn add đầy đủ để user thấy.
            if (series != null) {
                series.getData().clear();
                series.getData().add(new XYChart.Data<>(0, startingPrice));
                int idx = 1;
                double lastChartPrice = startingPrice;
                for (BidDto b : history) {
                    historyData.add(0, formatBidLine(b));
                    if (b.getAmount() > lastChartPrice) {
                        series.getData().add(new XYChart.Data<>(idx++, b.getAmount()));
                        lastChartPrice = b.getAmount();
                    }
                }
                bidIndex = idx;
            }

            // Auto-trigger UX nếu user mở phòng cho 1 phiên đã kết thúc
            // và họ chính là winner — tránh phải bấm thêm.
            long myId = Session.get().getUserId();
            boolean iAmWinner = a.getWinnerBidderId() != null
                    && a.getWinnerBidderId() == myId;
            if (iAmWinner && a.getStatus() != null) {
                if (a.getStatus() == com.bt.shared.Auction.AuctionStatus.FINISHED) {
                    promptPayment(a.getCurrentPrice(),
                            a.getWinnerUsername() != null
                                    ? a.getWinnerUsername()
                                    : Session.get().getUsername());
                } else if (a.getStatus() == com.bt.shared.Auction.AuctionStatus.PAID) {
                    promptRateSeller();
                }
            }
            System.out.println("[BiddingRoom] applyAuction DONE");
        } catch (RuntimeException ex) {
            System.err.println("[BiddingRoom] applyAuction error:");
            ex.printStackTrace();
            if (bidErrorLabel != null) {
                bidErrorLabel.setText("Lỗi hiển thị: " + ex.getMessage());
            }
        }
    }

    @FXML
    private void handleBid() {
        bidErrorLabel.setText("");
        long amount = MoneyTextField.parseValue(bidInput);
        if (amount <= 0) {
            bidErrorLabel.setText("Số tiền không hợp lệ — vui lòng nhập số dương");
            return;
        }
        if (!Session.get().isAuthenticated()) {
            bidErrorLabel.setText("Bạn chưa đăng nhập");
            return;
        }
        // Pre-check ngay phía client: amount phải > giá hiện tại đang hiển thị
        // (hiệu năng + UX — server vẫn validate lại để chống race / forgery)
        long bidderId = Session.get().getUserId();
        Long currentClientPrice = parseCurrentPriceLabel();
        if (currentClientPrice != null && amount <= currentClientPrice) {
            bidErrorLabel.setText("Giá đấu phải lớn hơn "
                    + MoneyFormat.vnd(currentClientPrice));
            return;
        }

        // Disable input/button trong khi gửi để tránh user spam (double-submit)
        bidInput.setDisable(true);

        new Thread(() -> {
            try {
                client.placeBid(auctionId, bidderId, amount);
                Platform.runLater(() -> {
                    bidInput.clear();
                    bidInput.setDisable(false);
                    bidErrorLabel.setText("");
                    bidInput.requestFocus();
                });
            } catch (AuctionClientException ex) {
                System.err.println("[Bid] Lỗi đặt giá: code=" + ex.getCode()
                        + " message=" + ex.getMessage());
                Platform.runLater(() -> {
                    bidInput.setDisable(false);
                    bidErrorLabel.setText(ex.getMessage());
                    Dialogs.error(ErrorMessages.title(ex.getCode()), ex.getMessage());
                });
            }
        }, "place-bid").start();
    }

    /**
     * Mở dialog đăng ký auto-bid. User nhập maxBid + increment, server sẽ
     * tự động bid hộ khi có đối thủ trả giá cao hơn (cho tới khi đạt maxBid).
     */
    @FXML
    private void handleAutoBid() {
        if (!Session.get().isAuthenticated()) {
            Dialogs.warn("Chưa đăng nhập", "Vui lòng đăng nhập trước.");
            return;
        }
        Long currentPrice = parseCurrentPriceLabel();
        long minNext = (currentPrice == null) ? 0 : currentPrice + 1;

        javafx.scene.control.Dialog<double[]> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Đăng ký Auto-bid");
        dialog.setHeaderText("Hệ thống sẽ tự bid hộ bạn khi có đối thủ trả cao hơn.\n"
                + "Giá hiện tại: " + (currentPrice == null ? "?" : MoneyFormat.vnd(currentPrice)));
        javafx.scene.control.DialogPane pane = dialog.getDialogPane();
        java.net.URL css = getClass().getResource("/com/bt/styles/app.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
        pane.getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(10, 4, 4, 4));

        Label maxLbl = new Label("Giá tối đa (maxBid):");
        maxLbl.getStyleClass().add("label-form");
        TextField maxField = new TextField();
        maxField.setPromptText("Vd: 50000000");
        maxField.setPrefWidth(220);
        MoneyTextField.install(maxField);

        Label incLbl = new Label("Bước giá (increment):");
        incLbl.getStyleClass().add("label-form");
        TextField incField = new TextField();
        incField.setPromptText("Vd: 100000");
        incField.setPrefWidth(220);
        MoneyTextField.install(incField);

        Label hint = new Label("• maxBid phải lớn hơn giá hiện tại\n"
                + "• Bước giá > 0\n"
                + "• Đăng ký lại sẽ ghi đè cấu hình cũ.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        grid.add(maxLbl, 0, 0); grid.add(maxField, 1, 0);
        grid.add(incLbl, 0, 1); grid.add(incField, 1, 1);
        grid.add(hint, 0, 2, 2, 1);
        pane.setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt != javafx.scene.control.ButtonType.OK) return null;
            long max = MoneyTextField.parseValue(maxField);
            long inc = MoneyTextField.parseValue(incField);
            if (max <= 0 || inc <= 0) {
                Dialogs.warn("Sai dữ liệu",
                        "maxBid và bước giá phải > 0");
                return null;
            }
            if (max <= minNext - 1) {
                Dialogs.warn("Sai dữ liệu",
                        "maxBid phải lớn hơn giá hiện tại "
                                + MoneyFormat.vnd(currentPrice));
                return null;
            }
            return new double[] { max, inc };
        });

        dialog.showAndWait().ifPresent(pair -> sendRegisterAutoBid(pair[0], pair[1]));
    }

    private void sendRegisterAutoBid(double maxBid, double increment) {
        autoBidBtn.setDisable(true);
        autoBidStatusLabel.setText("Đang đăng ký auto-bid...");
        new Thread(() -> {
            try {
                RegisterAutoBidResponse resp = client.registerAutoBid(
                        auctionId, maxBid, increment);
                Platform.runLater(() -> {
                    autoBidBtn.setDisable(false);
                    autoBidBtn.setText("✏ Sửa Auto-bid");
                    autoBidStatusLabel.setText(String.format(
                            "✅ Đã bật auto-bid: max %s, bước %s",
                            MoneyFormat.vnd(resp.getMaxBid()),
                            MoneyFormat.vnd(resp.getIncrement())));
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> {
                    autoBidBtn.setDisable(false);
                    autoBidStatusLabel.setText("❌ " + ex.getMessage());
                    Dialogs.error(ErrorMessages.title(ex.getCode()), ex.getMessage());
                });
            }
        }, "register-autobid").start();
    }

    /**
     * Parse số tiền đang hiển thị ở currentPriceLabel (định dạng VND).
     * Trả null nếu chưa load xong (label = "$0" hoặc rỗng).
     */
    private Long parseCurrentPriceLabel() {
        if (currentPriceLabel == null) return null;
        String txt = currentPriceLabel.getText();
        if (txt == null || txt.isEmpty()) return null;
        String digits = txt.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @FXML
    private void back() {
        cleanup();
        SceneManager.get().switchTo("dashboard");
    }

    private void cleanup() {
        if (eventListener != null) {
            ServerConnection.getInstance().removeEventListener(eventListener);
            eventListener = null;
        }
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
        try {
            client.unsubscribe(auctionId);
        } catch (AuctionClientException ignored) {
            // Best-effort — connection có thể đã đóng
        }
    }

    /** Listener nhận event từ server — chạy trên thread network. */
    private void onServerEvent(Message msg) {
        if (msg.getType() == MessageType.BID_PLACED_EVENT) {
            BidPlacedEvent ev = MessageCodec.payloadAs(msg, BidPlacedEvent.class);
            if (ev.getAuctionId() != auctionId) return;
            Platform.runLater(() -> applyBidPlaced(ev));
        } else if (msg.getType() == MessageType.AUCTION_FINISHED_EVENT) {
            AuctionFinishedEvent fin = MessageCodec.payloadAs(msg, AuctionFinishedEvent.class);
            if (fin.getAuctionId() != auctionId) return;
            Platform.runLater(() -> handleAuctionFinished(fin));
        }
    }

    /**
     * Khi phiên kết thúc:
     *  - Update UI badge + countdown
     *  - Nếu user là winner → hỏi thanh toán ngay
     *  - Nếu không phải winner → chỉ thông báo
     */
    private void handleAuctionFinished(AuctionFinishedEvent fin) {
        applyStatusBadge("FINISHED");
        if (countdownLabel != null) countdownLabel.setText("Đã kết thúc");

        long myId = Session.get().getUserId();
        boolean iWon = fin.getWinnerBidderId() != null
                && fin.getWinnerBidderId() == myId;

        if (iWon) {
            promptPayment(fin.getFinalPrice(), fin.getWinnerUsername());
        } else {
            String winner = fin.getWinnerUsername() != null
                    ? fin.getWinnerUsername() : "(không có)";
            Dialogs.info("Phiên kết thúc",
                    "Phiên đấu giá đã kết thúc.\nNgười thắng: " + winner
                            + "\nGiá chốt: " + MoneyFormat.vnd(fin.getFinalPrice()));
        }
    }

    /** Dialog xác nhận thanh toán cho winner. */
    private void promptPayment(double price, String winnerUsername) {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Bạn đã thắng phiên với giá " + MoneyFormat.vnd(price)
                        + "\n\nThanh toán ngay?",
                javafx.scene.control.ButtonType.YES,
                javafx.scene.control.ButtonType.NO);
        confirm.setTitle("🏆 Chúc mừng " + winnerUsername);
        confirm.setHeaderText("Bạn là người thắng cuộc");
        com.bt.client.util.Dialogs.applyStyle(confirm.getDialogPane());
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == javafx.scene.control.ButtonType.YES) {
                doPayment();
            } else {
                Dialogs.info("Chưa thanh toán",
                        "Bạn có thể quay lại Dashboard để thanh toán sau.");
            }
        });
    }

    private void doPayment() {
        new Thread(() -> {
            try {
                PayAuctionResponse resp = client.payAuction(auctionId);
                Platform.runLater(() -> {
                    Dialogs.info("✅ Thanh toán thành công",
                            "Đã trừ " + MoneyFormat.vnd(resp.getPaidAmount())
                                    + "\nSố dư còn lại: " + MoneyFormat.vnd(resp.getNewBalance()));
                    promptRateSeller();
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() ->
                        Dialogs.error("Thanh toán thất bại", ex.getMessage()));
            }
        }, "pay-auction").start();
    }

    /** Dialog đánh giá seller (1-5 sao + comment). Bidder có thể skip. */
    private void promptRateSeller() {
        javafx.scene.control.Dialog<int[]> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle("Đánh giá người bán");
        dlg.setHeaderText("Bạn cho seller mấy sao? (1-5)");
        com.bt.client.util.Dialogs.applyStyle(dlg.getDialogPane());
        dlg.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10);
        box.setPadding(new javafx.geometry.Insets(8));

        javafx.scene.control.Slider slider = new javafx.scene.control.Slider(1, 5, 5);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setBlockIncrement(1);

        javafx.scene.control.Label starsLbl = new javafx.scene.control.Label("⭐⭐⭐⭐⭐ (5 sao)");
        slider.valueProperty().addListener((o, a, b) -> {
            int s = b.intValue();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s; i++) sb.append("⭐");
            starsLbl.setText(sb + " (" + s + " sao)");
        });

        javafx.scene.control.TextArea comment = new javafx.scene.control.TextArea();
        comment.setPromptText("Nhận xét (tùy chọn, tối đa 1000 ký tự)");
        comment.setPrefRowCount(4);
        comment.setWrapText(true);

        box.getChildren().addAll(slider, starsLbl,
                new javafx.scene.control.Label("Nhận xét:"), comment);
        dlg.getDialogPane().setContent(box);

        dlg.setResultConverter(bt -> {
            if (bt != javafx.scene.control.ButtonType.OK) return null;
            return new int[] { (int) slider.getValue(),
                    comment.getText() == null ? 0 : comment.getText().length() };
        });

        dlg.showAndWait().ifPresent(arr -> {
            int stars = arr[0];
            String txt = comment.getText();
            new Thread(() -> {
                try {
                    RateSellerResponse resp = client.rateSeller(auctionId, stars, txt);
                    Platform.runLater(() -> Dialogs.info("Cảm ơn bạn",
                            "Đánh giá đã được lưu.\nĐiểm trung bình mới của seller: "
                                    + String.format("%.2f ⭐ (%d lượt đánh giá)",
                                            resp.getNewAverageRating(),
                                            resp.getTotalRatings())));
                } catch (AuctionClientException ex) {
                    Platform.runLater(() ->
                            Dialogs.error("Lỗi đánh giá", ex.getMessage()));
                }
            }, "rate-seller").start();
        });
    }

    private void applyBidPlaced(BidPlacedEvent ev) {
        // Anti-out-of-order: nếu event đến trễ và amount THẤP HƠN giá đang
        // hiển thị, bỏ qua việc cập nhật giá/leader/chart — chỉ append vào
        // lịch sử để user vẫn thấy đầy đủ. Giá đấu là MONOTONIC không giảm.
        Long shown = parseCurrentPriceLabel();
        boolean isNewer = shown == null || ev.getAmount() > shown;

        if (isNewer) {
            if (currentPriceLabel != null) {
                currentPriceLabel.setText(MoneyFormat.vnd(ev.getAmount()));
            }
            if (leaderLabel != null) {
                leaderLabel.setText("Dẫn đầu: " + ev.getBidderUsername());
            }
            if (ev.getNewEndTime() != null) {
                auctionEnd = ev.getNewEndTime();
                if (endTimeLabel != null) {
                    endTimeLabel.setText("Kết thúc: " + auctionEnd.format(HMS));
                }
            }
        } else {
            System.out.println("[BiddingRoom] Bỏ qua bid out-of-order: ev="
                    + ev.getAmount() + " <= shown=" + shown);
        }

        BidDto fake = new BidDto();
        fake.setBidderUsername(ev.getBidderUsername());
        fake.setAmount(ev.getAmount());
        fake.setBidTime(ev.getBidTime());
        historyData.add(0, formatBidLine(fake));

        // Chart: chỉ append điểm nếu giá MỚI HƠN giá điểm cuối — đảm bảo
        // đường biểu đồ không bao giờ rơi xuống.
        if (series != null && isNewer) {
            series.getData().add(new XYChart.Data<>(bidIndex++, ev.getAmount()));
            if (series.getData().size() > 200) {
                series.getData().remove(0, 50);
            }
        }
    }

    private String formatBidLine(BidDto b) {
        return (b.getBidTime() == null ? "?" : b.getBidTime().format(HMS))
                + "   " + MoneyFormat.vnd(b.getAmount())
                + "   by " + (b.getBidderUsername() == null ? "?" : b.getBidderUsername());
    }

    /** Cập nhật status badge với class CSS phù hợp. */
    private void applyStatusBadge(String status) {
        statusLabel.setText(status);
        statusLabel.getStyleClass().removeAll("badge-running", "badge-open",
                "badge-finished", "badge-canceled");
        switch (status) {
            case "RUNNING":  statusLabel.getStyleClass().add("badge-running"); break;
            case "OPEN":     statusLabel.getStyleClass().add("badge-open"); break;
            case "CANCELED": statusLabel.getStyleClass().add("badge-canceled"); break;
            default:         statusLabel.getStyleClass().add("badge-finished"); break;
        }
    }


    private void updateCountdown() {
        if (auctionEnd == null) return;
        LocalDateTime now = LocalDateTime.now();
        long secs = ChronoUnit.SECONDS.between(now, auctionEnd);
        if (secs <= 0) {
            countdownLabel.setText("⏳ Đã hết giờ");
            return;
        }
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        countdownLabel.setText(String.format("⏳ %02d:%02d:%02d", h, m, s));
    }

    private void showError(AuctionClientException ex) {
        Dialogs.error(ErrorMessages.title(ex.getCode()), ex.getMessage());
    }
}
