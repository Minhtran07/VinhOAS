package com.bt.client.controller;

import com.bt.client.net.AuctionClient;
import com.bt.client.net.AuctionClientException;
import com.bt.client.net.ServerConnection;
import com.bt.client.session.Session;
import com.bt.client.ui.SceneManager;
import com.bt.client.util.Dialogs;
import com.bt.client.util.ErrorMessages;
import com.bt.client.util.MoneyFormat;
import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.UserRole;
import com.bt.shared.protocol.Message;
import com.bt.shared.protocol.MessageCodec;
import com.bt.shared.protocol.MessageType;
import com.bt.shared.protocol.dto.AuctionCreatedEvent;
import com.bt.shared.protocol.dto.AuctionDto;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dashboard hiển thị danh sách phiên đấu giá.
 *
 * Realtime: lắng nghe {@link MessageType#AUCTION_CREATED_EVENT} từ
 * ServerConnection — khi có seller tạo phiên mới, dashboard tự refresh
 * danh sách (không cần bấm Refresh thủ công).
 *
 * Filter theo status, double-click row → mở Bidding Room.
 */
public class DashboardController {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    @FXML private Label userInfoLabel;
    @FXML private ChoiceBox<String> statusFilter;
    @FXML private Button sellerBtn;
    @FXML private Button adminBtn;
    @FXML private TableView<AuctionDto> auctionTable;
    @FXML private TableColumn<AuctionDto, Number> colId;
    @FXML private TableColumn<AuctionDto, String> colName;
    @FXML private TableColumn<AuctionDto, String> colCategory;
    @FXML private TableColumn<AuctionDto, String> colCurrent;
    @FXML private TableColumn<AuctionDto, String> colStart;
    @FXML private TableColumn<AuctionDto, String> colEnd;
    @FXML private TableColumn<AuctionDto, String> colStatus;
    @FXML private TableColumn<AuctionDto, Void> colAction;
    @FXML private Label statusLabel;

    private final AuctionClient client = new AuctionClient();
    private final ObservableList<AuctionDto> data = FXCollections.observableArrayList();
    private Consumer<Message> eventListener;
    private Consumer<ServerConnection.LifecycleEvent> lifecycleListener;

    @FXML
    public void initialize() {
        Session s = Session.get();
        userInfoLabel.setText(s.isAuthenticated()
                ? "[" + s.getRole() + "] " + s.getUsername()
                : "(chưa đăng nhập)");

        sellerBtn.setVisible(s.getRole() == UserRole.SELLER);
        adminBtn.setVisible(s.getRole() == UserRole.ADMIN);

        statusFilter.setItems(FXCollections.observableArrayList(
                "ALL", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED"));
        statusFilter.setValue("RUNNING");
        statusFilter.setOnAction(e -> refresh());

        colId.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getAuctionId()));
        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getItemName()));
        colCategory.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getItemCategory())));
        colCurrent.setCellValueFactory(c ->
                new SimpleStringProperty(MoneyFormat.vnd(c.getValue().getCurrentPrice())));
        colStart.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStartTime() != null
                        ? c.getValue().getStartTime().format(DT_FMT) : ""));
        colEnd.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndTime() != null
                        ? c.getValue().getEndTime().format(DT_FMT) : ""));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getStatus())));

        // Render status thành badge có màu
        colStatus.setCellFactory(col -> new TableCell<AuctionDto, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("badge", "badge-running", "badge-open",
                        "badge-finished", "badge-canceled");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(item);
                badge.getStyleClass().addAll("badge", badgeClassFor(item));
                setText(null);
                setGraphic(badge);
            }
        });

        // Cột HÀNH ĐỘNG: render Button. Logic phụ thuộc trạng thái + role:
        //  - Phiên RUNNING/OPEN: "VÀO ĐẤU GIÁ" (bidder/seller-khác)
        //  - Phiên FINISHED + bạn là winner & chưa PAID: "💳 Thanh toán"
        //  - Phiên PAID + bạn là winner: "Đánh giá seller" (nếu chưa rate)
        //  - Phiên của chính seller: nhãn disabled
        colAction.setCellFactory(col -> new TableCell<AuctionDto, Void>() {
            private final Button btn = new Button();
            {
                btn.getStyleClass().add("button-accent");
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setPrefHeight(32);
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                AuctionDto item = (AuctionDto) getTableRow().getItem();
                long myId = Session.get().getUserId();
                UserRole myRole = Session.get().getRole();
                boolean ownAuction = myRole == UserRole.SELLER
                        && item.getSellerId() == myId;
                boolean iAmWinner = item.getWinnerBidderId() != null
                        && item.getWinnerBidderId() == myId;
                String status = String.valueOf(item.getStatus());

                btn.setDisable(false);
                btn.getStyleClass().removeAll("button-primary");

                if (ownAuction) {
                    btn.setText("(Phiên của bạn)");
                    btn.setDisable(true);
                    btn.setOnAction(null);
                } else if ("FINISHED".equals(status) && iAmWinner) {
                    btn.setText("💳 Thanh toán");
                    btn.setOnAction(e -> openBiddingRoom(item));
                } else if ("PAID".equals(status) && iAmWinner) {
                    btn.setText("⭐ Đánh giá seller");
                    btn.getStyleClass().add("button-primary");
                    btn.setOnAction(e -> openBiddingRoom(item));
                } else if ("FINISHED".equals(status) || "PAID".equals(status)
                        || "CANCELED".equals(status)) {
                    btn.setText("Xem phiên");
                    btn.setOnAction(e -> openBiddingRoom(item));
                } else {
                    btn.setText("VÀO ĐẤU GIÁ");
                    btn.setOnAction(e -> openBiddingRoom(item));
                }
                setGraphic(btn);
            }
        });

        auctionTable.setItems(data);
        // Double-click row vẫn dùng được — backup
        auctionTable.setRowFactory(tv -> {
            TableRow<AuctionDto> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty() && row.getItem() != null) {
                    openBiddingRoom(row.getItem());
                }
            });
            return row;
        });

        // Realtime: nhận AUCTION_CREATED_EVENT → tự refresh
        eventListener = this::onServerEvent;
        ServerConnection.getInstance().addEventListener(eventListener);

        // Hiển thị trạng thái kết nối ở status label
        lifecycleListener = ev -> Platform.runLater(() -> {
            switch (ev) {
                case DISCONNECTED:
                    statusLabel.setText("⚠️ Mất kết nối server — đang thử lại...");
                    break;
                case RECONNECTING:
                    statusLabel.setText("🔄 Đang kết nối lại...");
                    break;
                case RECONNECTED:
                    statusLabel.setText("✅ Đã kết nối lại — đang refresh");
                    refresh();
                    break;
                default:
                    break;
            }
        });
        ServerConnection.getInstance().addLifecycleListener(lifecycleListener);

        refresh();
    }

    private void onServerEvent(Message msg) {
        if (msg.getType() == MessageType.AUCTION_CREATED_EVENT) {
            AuctionCreatedEvent ev = MessageCodec.payloadAs(msg, AuctionCreatedEvent.class);
            AuctionDto a = ev.getAuction();
            // Chỉ thêm vào table nếu phù hợp với filter hiện tại
            String filter = statusFilter.getValue();
            if ("ALL".equals(filter) || filter.equals(String.valueOf(a.getStatus()))) {
                Platform.runLater(() -> {
                    // Tránh duplicate nếu refresh thủ công đã thêm
                    boolean exists = data.stream()
                            .anyMatch(d -> d.getAuctionId() == a.getAuctionId());
                    if (!exists) {
                        data.add(0, a);
                        statusLabel.setText("📢 Phiên mới: " + a.getItemName());
                    }
                });
            }
        } else if (msg.getType() == MessageType.AUCTION_PAID_EVENT
                || msg.getType() == MessageType.AUCTION_FINISHED_EVENT) {
            // Status đổi → refresh để row hiển thị nút phù hợp
            Platform.runLater(this::refresh);
        }
    }

    @FXML
    public void refresh() {
        String filter = statusFilter.getValue();
        AuctionStatus status = "ALL".equals(filter) ? null : AuctionStatus.valueOf(filter);
        statusLabel.setText("Đang tải...");

        new Thread(() -> {
            try {
                List<AuctionDto> list = client.listAuctions(status);
                Platform.runLater(() -> {
                    // Clear selection trước khi setAll để selection-listener
                    // không trigger openBiddingRoom khi reload data
                    auctionTable.getSelectionModel().clearSelection();
                    data.setAll(list);
                    statusLabel.setText("Tải " + list.size() + " phiên — "
                            + java.time.LocalTime.now().withNano(0));
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> showError(ex));
            }
        }, "load-auctions").start();
    }

    /**
     * Action handler cho nút "VÀO PHÒNG ĐẤU GIÁ" — dùng selected row.
     */
    @FXML
    public void enterRoom() {
        AuctionDto chosen = auctionTable.getSelectionModel().getSelectedItem();
        if (chosen == null && !data.isEmpty()) {
            chosen = data.get(0);
            auctionTable.getSelectionModel().select(0);
        }
        if (chosen == null) {
            Dialogs.warn("Chưa có phiên",
                    "Hiện không có phiên nào để mở. Hãy chờ seller tạo phiên.");
            return;
        }
        openBiddingRoom(chosen);
    }

    /**
     * Mở phòng đấu giá cho 1 AuctionDto cụ thể. Logic chung cho click row,
     * double-click, hoặc nút "VÀO PHÒNG".
     */
    private void openBiddingRoom(AuctionDto chosen) {
        System.out.println("[Dashboard] >>> openBiddingRoom called, chosen="
                + (chosen == null ? "null" : chosen.getItemName()
                        + " #" + chosen.getAuctionId()));
        if (chosen == null) {
            System.out.println("[Dashboard] openBiddingRoom: chosen=null, abort");
            return;
        }
        try {
            // Bảo vệ: seller không tự đấu giá item của mình
            if (Session.get().getRole() == UserRole.SELLER
                    && chosen.getSellerId() == Session.get().getUserId()) {
                Dialogs.warn("Không cho phép",
                        "Bạn không thể tự đấu giá sản phẩm của chính mình.");
                return;
            }
            statusLabel.setText("Đang mở phòng đấu giá #" + chosen.getAuctionId() + "...");
            BiddingRoomController.setActiveAuctionId(chosen.getAuctionId());
            System.out.println("[Dashboard] activeAuctionId set, calling switchTo...");
            // KHÔNG cleanup eventListener trước switchTo — nếu switch fail,
            // user vẫn ở Dashboard và cần listener để nhận AUCTION_CREATED_EVENT.
            // Cleanup sẽ chạy ở back button của BiddingRoom (nếu ngược lại) hoặc
            // ở openSeller/openAdmin/logout.
            SceneManager.get().switchTo("bidding");
            // Sau khi switchTo thành công, controller cũ bị thay thế nên
            // listener "leak" nhưng không hại nhiều — Dashboard.initialize
            // bên trên sẽ dọn ở lần quay về.
            cleanup();
            System.out.println("[Dashboard] openBiddingRoom DONE");
        } catch (Throwable ex) {
            // Bắt cả Error (vd: NoClassDefFoundError) ngoài RuntimeException —
            // tránh để app im lặng khi lỗi nặng xảy ra trong switchTo.
            System.err.println("[Dashboard] openBiddingRoom FAIL:");
            ex.printStackTrace();
            Throwable rootCause = ex;
            while (rootCause.getCause() != null) rootCause = rootCause.getCause();
            String detail = rootCause.getClass().getSimpleName()
                    + ": " + rootCause.getMessage();
            statusLabel.setText("❌ Lỗi mở phòng: " + detail);
            Dialogs.error("Lỗi mở phòng đấu giá", detail);
        }
    }

    @FXML
    public void openSeller() {
        cleanup();
        SceneManager.get().switchTo("seller");
    }

    @FXML
    public void openAdmin() {
        cleanup();
        SceneManager.get().switchTo("admin");
    }

    @FXML
    public void logout() {
        cleanup();
        Session.get().clear();
        SceneManager.get().switchTo("login");
    }

    /** Gỡ event listener để khi controller bị thay thế không leak. */
    private void cleanup() {
        if (eventListener != null) {
            ServerConnection.getInstance().removeEventListener(eventListener);
            eventListener = null;
        }
        if (lifecycleListener != null) {
            ServerConnection.getInstance().removeLifecycleListener(lifecycleListener);
            lifecycleListener = null;
        }
    }

    private void showError(AuctionClientException ex) {
        statusLabel.setText(ex.getMessage());
        Dialogs.error(ErrorMessages.title(ex.getCode()), ex.getMessage());
    }

    /** Map từ status string sang CSS class cho badge. */
    private static String badgeClassFor(String status) {
        switch (status) {
            case "RUNNING": return "badge-running";
            case "OPEN":    return "badge-open";
            case "FINISHED":
            case "PAID":    return "badge-finished";
            case "CANCELED":return "badge-canceled";
            default:        return "badge-finished";
        }
    }
}
