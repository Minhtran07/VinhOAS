package com.bt.client.controller;

import com.bt.client.net.AuctionClient;
import com.bt.client.net.AuctionClientException;
import com.bt.client.session.Session;
import com.bt.client.ui.SceneManager;
import com.bt.client.util.Dialogs;
import com.bt.client.util.ErrorMessages;
import com.bt.client.util.MoneyFormat;
import com.bt.shared.UserRole;
import com.bt.shared.protocol.dto.SetUserActiveResponse;
import com.bt.shared.protocol.dto.UserSummaryDto;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Admin panel: quản lý user (xem danh sách, ban/unban).
 *
 * UI là một bảng user với filter theo role + trạng thái active. Mỗi row có
 * nút BAN/UNBAN tương ứng. Server enforce role ADMIN, client cũng kiểm tra
 * để fail-fast tránh round trip không cần thiết.
 *
 * Tất cả network call chạy trên thread phụ — UI thread chỉ render kết quả
 * thông qua {@link Platform#runLater}.
 */
public class AdminController {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label userInfoLabel;
    @FXML private ChoiceBox<String> roleFilter;
    @FXML private ChoiceBox<String> activeFilter;
    @FXML private TableView<UserSummaryDto> userTable;
    @FXML private TableColumn<UserSummaryDto, Number> colId;
    @FXML private TableColumn<UserSummaryDto, String> colUsername;
    @FXML private TableColumn<UserSummaryDto, String> colEmail;
    @FXML private TableColumn<UserSummaryDto, String> colRole;
    @FXML private TableColumn<UserSummaryDto, String> colExtra;
    @FXML private TableColumn<UserSummaryDto, Boolean> colActive;
    @FXML private TableColumn<UserSummaryDto, String> colCreated;
    @FXML private TableColumn<UserSummaryDto, Void> colAction;
    @FXML private Label statusLabel;

    private final AuctionClient client = new AuctionClient();
    private final ObservableList<UserSummaryDto> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        Session s = Session.get();
        userInfoLabel.setText(s.isAuthenticated()
                ? "[" + s.getRole() + "] " + s.getUsername()
                : "(chưa đăng nhập)");

        // Defensive: nếu non-admin lỡ vào được scene này, hiện cảnh báo
        if (s.getRole() != UserRole.ADMIN) {
            statusLabel.setText("⚠ Bạn không có quyền admin — quay về Dashboard");
            Dialogs.warn("Không có quyền",
                    "Chỉ tài khoản ADMIN mới được truy cập trang này.");
        }

        roleFilter.setItems(FXCollections.observableArrayList(
                "ALL", "BIDDER", "SELLER", "ADMIN"));
        roleFilter.setValue("ALL");
        roleFilter.setOnAction(e -> refresh());

        activeFilter.setItems(FXCollections.observableArrayList(
                "ALL", "ACTIVE", "BANNED"));
        activeFilter.setValue("ALL");
        activeFilter.setOnAction(e -> refresh());

        colId.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().getUserId()));
        colUsername.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUsername()));
        colEmail.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEmail()));
        colRole.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getRole())));
        colExtra.setCellValueFactory(c ->
                new SimpleStringProperty(extraInfoOf(c.getValue())));
        colActive.setCellValueFactory(c ->
                new SimpleBooleanProperty(c.getValue().isActive()));
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCreatedAt() != null
                        ? c.getValue().getCreatedAt().format(DT_FMT) : ""));

        // Render active thành badge
        colActive.setCellFactory(col -> new TableCell<UserSummaryDto, Boolean>() {
            @Override
            protected void updateItem(Boolean active, boolean empty) {
                super.updateItem(active, empty);
                getStyleClass().removeAll("badge", "badge-running", "badge-canceled");
                if (empty || active == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(active ? "ACTIVE" : "BANNED");
                badge.getStyleClass().addAll(
                        "badge", active ? "badge-running" : "badge-canceled");
                setText(null);
                setGraphic(badge);
            }
        });

        // Cột hành động: BAN/UNBAN
        colAction.setCellFactory(col -> new TableCell<UserSummaryDto, Void>() {
            private final Button btn = new Button();
            {
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setPrefHeight(30);
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                UserSummaryDto u = (UserSummaryDto) getTableRow().getItem();
                btn.getStyleClass().removeAll("button-primary", "button-ghost");

                // Không cho admin tự ban chính mình
                boolean isSelf = Session.get().getUserId() == u.getUserId();
                if (isSelf) {
                    btn.setText("(Bạn)");
                    btn.setDisable(true);
                    btn.setOnAction(null);
                } else if (u.isActive()) {
                    btn.setText("🚫 BAN");
                    btn.getStyleClass().add("button-ghost");
                    btn.setDisable(false);
                    btn.setOnAction(e -> confirmAndSetActive(u, false));
                } else {
                    btn.setText("✓ UNBAN");
                    btn.getStyleClass().add("button-primary");
                    btn.setDisable(false);
                    btn.setOnAction(e -> confirmAndSetActive(u, true));
                }
                setGraphic(btn);
            }
        });

        userTable.setItems(data);
        refresh();
    }

    @FXML
    public void refresh() {
        UserRole rf = parseRoleFilter(roleFilter.getValue());
        Boolean af = parseActiveFilter(activeFilter.getValue());
        statusLabel.setText("Đang tải...");

        new Thread(() -> {
            try {
                List<UserSummaryDto> list = client.listUsers(rf, af);
                Platform.runLater(() -> {
                    data.setAll(list);
                    statusLabel.setText("Tải " + list.size() + " user — "
                            + java.time.LocalTime.now().withNano(0));
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> showError(ex));
            }
        }, "load-users").start();
    }

    private void confirmAndSetActive(UserSummaryDto u, boolean newActive) {
        String action = newActive ? "UNBAN" : "BAN";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc muốn " + action + " user '" + u.getUsername() + "'?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Xác nhận " + action);
        confirm.setHeaderText(action + " tài khoản #" + u.getUserId());
        Dialogs.applyStyle(confirm.getDialogPane());
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        statusLabel.setText("Đang " + action + " user " + u.getUsername() + "...");
        new Thread(() -> {
            try {
                SetUserActiveResponse resp =
                        client.setUserActive(u.getUserId(), newActive);
                Platform.runLater(() -> {
                    if (resp.isSuccess()) {
                        // Update tại chỗ thay vì reload toàn bộ
                        u.setActive(resp.isActive());
                        userTable.refresh();
                        statusLabel.setText("✅ " + action + " " + u.getUsername()
                                + " thành công");
                    } else {
                        statusLabel.setText("❌ Server từ chối " + action
                                + " — thử refresh");
                        Dialogs.error("Thao tác thất bại",
                                "Không thể " + action + " user. Vui lòng refresh và thử lại.");
                    }
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> showError(ex));
            }
        }, "set-user-active").start();
    }

    @FXML
    private void back() {
        SceneManager.get().switchTo("dashboard");
    }

    // ---------- Helpers ----------

    private void showError(AuctionClientException ex) {
        statusLabel.setText(ex.getMessage());
        Dialogs.error(ErrorMessages.title(ex.getCode()), ex.getMessage());
    }

    private static UserRole parseRoleFilter(String s) {
        if (s == null || "ALL".equals(s)) return null;
        return UserRole.fromString(s);
    }

    private static Boolean parseActiveFilter(String s) {
        if (s == null || "ALL".equals(s)) return null;
        return "ACTIVE".equals(s);
    }

    /** Cột "Thông tin thêm" — hiển thị balance/rating/access level tuỳ role. */
    private static String extraInfoOf(UserSummaryDto u) {
        if (u.getRole() == null) return "";
        switch (u.getRole()) {
            case BIDDER: return "💰 " + MoneyFormat.vnd(u.getAccountBalance());
            case SELLER: return "⭐ " + String.format("%.1f", u.getSellerRating());
            case ADMIN:  return "🛡 Level " + u.getAccessLevel();
            default:     return "";
        }
    }
}
