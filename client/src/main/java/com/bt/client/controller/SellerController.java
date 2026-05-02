package com.bt.client.controller;

import com.bt.client.net.AuctionClient;
import com.bt.client.net.AuctionClientException;
import com.bt.client.session.Session;
import com.bt.client.ui.SceneManager;
import com.bt.client.util.Dialogs;
import com.bt.client.util.ErrorMessages;
import com.bt.client.util.MoneyFormat;
import com.bt.client.util.MoneyTextField;
import com.bt.shared.ItemCategory;
import com.bt.shared.ItemFactory;
import com.bt.shared.protocol.dto.AuctionDto;
import com.bt.shared.protocol.dto.CreateAuctionRequest;
import com.bt.shared.protocol.dto.CreateItemRequest;
import com.bt.shared.protocol.dto.ItemDto;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.GridPane;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Màn hình Seller: xem item của mình, đăng item mới, đưa item lên đấu giá.
 *
 * Form spec đổi field theo category được chọn (Electronics: brand+warranty,
 * Art: artist+year, Vehicle: make+model+mileage). Tất cả form đều validate
 * client-side trước, gọi server, hiển thị error theo error code.
 */
public class SellerController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @FXML private Label sellerInfoLabel;
    @FXML private Label leftStatusLabel;
    @FXML private Label rightStatusLabel;

    @FXML private TableView<ItemDto> itemTable;
    @FXML private TableColumn<ItemDto, Number> colItemId;
    @FXML private TableColumn<ItemDto, String> colItemName;
    @FXML private TableColumn<ItemDto, String> colItemCategory;
    @FXML private TableColumn<ItemDto, String> colItemPrice;
    @FXML private TableColumn<ItemDto, String> colItemAuction;
    @FXML private Button auctionBtn;

    @FXML private ChoiceBox<ItemCategory> categoryChoice;
    @FXML private TextField nameField;
    @FXML private TextArea descField;
    @FXML private TextField priceField;
    @FXML private javafx.scene.layout.VBox specBox;

    private final AuctionClient client = new AuctionClient();
    private final ObservableList<ItemDto> items = FXCollections.observableArrayList();

    // Field động theo category được render trong specGrid
    private TextField specField1;
    private TextField specField2;
    private TextField specField3;

    @FXML
    public void initialize() {
        Session s = Session.get();
        sellerInfoLabel.setText("[" + s.getRole() + "] " + s.getUsername());

        colItemId.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getItemId()));
        colItemName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colItemCategory.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getCategory())));
        colItemPrice.setCellValueFactory(c ->
                new SimpleStringProperty(MoneyFormat.vnd(c.getValue().getStartingPrice())));
        colItemAuction.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isHasActiveAuction()
                        ? "🔴 Đang chạy" : "—"));
        itemTable.setItems(items);

        categoryChoice.setItems(FXCollections.observableArrayList(ItemCategory.values()));
        categoryChoice.setValue(ItemCategory.ELECTRONICS);
        categoryChoice.setOnAction(e -> renderSpecFields());
        renderSpecFields();

        // Auto-format dấu phẩy mỗi 3 chữ số khi user gõ giá
        MoneyTextField.install(priceField);

        refreshItems();
    }

    /**
     * Render lại specBox theo category đang chọn.
     * Mỗi field là 1 VBox (label + textfield) — full width, không bị bó.
     */
    private void renderSpecFields() {
        specBox.getChildren().clear();
        specField1 = null;
        specField2 = null;
        specField3 = null;

        ItemCategory cat = categoryChoice.getValue();
        if (cat == null) return;

        switch (cat) {
            case ELECTRONICS:
                specField1 = appendField("Hãng sản xuất", "vd: Apple, Samsung");
                specField2 = appendField("Bảo hành (tháng)", "vd: 12");
                break;
            case ART:
                specField1 = appendField("Tác giả", "vd: Vincent van Gogh");
                specField2 = appendField("Năm sáng tác", "vd: 1885");
                break;
            case VEHICLE:
                specField1 = appendField("Hãng xe", "vd: Toyota, Honda");
                specField2 = appendField("Dòng xe", "vd: Camry");
                specField3 = appendField("Số km đã đi", "vd: 45000");
                break;
        }
    }

    /** Tạo 1 ô input có label phía trên, thêm vào specBox. Trả về TextField. */
    private TextField appendField(String labelText, String prompt) {
        Label lbl = new Label(labelText.toUpperCase());
        lbl.getStyleClass().add("label-form");
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefHeight(40);
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(4, lbl, field);
        specBox.getChildren().add(box);
        return field;
    }

    @FXML
    public void refreshItems() {
        leftStatusLabel.setText("Đang tải...");
        new Thread(() -> {
            try {
                List<ItemDto> list = client.listMyItems();
                Platform.runLater(() -> {
                    items.setAll(list);
                    leftStatusLabel.setText("Có " + list.size() + " sản phẩm");
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> showError(leftStatusLabel, ex));
            }
        }, "list-my-items").start();
    }

    @FXML
    public void handleCreateItem() {
        String name = nameField.getText();
        String desc = descField.getText();
        String priceText = priceField.getText();
        ItemCategory cat = categoryChoice.getValue();

        if (name == null || name.trim().isEmpty()) {
            rightStatusLabel.setText("Tên không được để trống");
            return;
        }
        // MoneyTextField đã strip ký tự non-digit và format. Lấy giá trị thuần.
        long price = MoneyTextField.parseValue(priceField);
        if (price <= 0) {
            rightStatusLabel.setText("Giá phải > 0");
            return;
        }

        Map<String, Object> spec = new HashMap<>();
        try {
            switch (cat) {
                case ELECTRONICS:
                    spec.put(ItemFactory.KEY_BRAND, requireText(specField1, "Brand"));
                    spec.put(ItemFactory.KEY_WARRANTY_MONTHS,
                            Integer.parseInt(requireText(specField2, "Warranty")));
                    break;
                case ART:
                    spec.put(ItemFactory.KEY_ARTIST, requireText(specField1, "Artist"));
                    spec.put(ItemFactory.KEY_YEAR_CREATED,
                            Integer.parseInt(requireText(specField2, "Year")));
                    break;
                case VEHICLE:
                    spec.put(ItemFactory.KEY_MAKE, requireText(specField1, "Make"));
                    spec.put(ItemFactory.KEY_MODEL, requireText(specField2, "Model"));
                    spec.put(ItemFactory.KEY_MILEAGE,
                            Integer.parseInt(requireText(specField3, "Mileage")));
                    break;
            }
        } catch (IllegalArgumentException ex) {
            rightStatusLabel.setText(ex.getMessage());
            return;
        }

        CreateItemRequest req = new CreateItemRequest();
        req.setName(name.trim());
        req.setDescription(desc == null ? "" : desc.trim());
        req.setStartingPrice(price);
        req.setCategory(cat);
        req.setSpec(spec);

        rightStatusLabel.setText("Đang tạo...");
        new Thread(() -> {
            try {
                ItemDto created = client.createItem(req);
                Platform.runLater(() -> {
                    rightStatusLabel.setText("Đã tạo item id=" + created.getItemId());
                    nameField.clear();
                    descField.clear();
                    priceField.clear();
                    if (specField1 != null) specField1.clear();
                    if (specField2 != null) specField2.clear();
                    if (specField3 != null) specField3.clear();
                    refreshItems();
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> showError(rightStatusLabel, ex));
            }
        }, "create-item").start();
    }

    /**
     * Mở dialog chọn thời lượng phiên (theo giờ).
     *
     * Phiên đấu giá luôn bắt đầu NGAY tại thời điểm tạo, kéo dài N giờ
     * theo lựa chọn của seller. Cách này đơn giản hơn nhiều so với cho
     * người dùng nhập timestamp.
     */
    @FXML
    public void openAuctionDialog() {
        ItemDto selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            leftStatusLabel.setText("Chọn 1 item trước");
            return;
        }
        if (selected.isHasActiveAuction()) {
            leftStatusLabel.setText("Item này đã có phiên đang chạy");
            return;
        }

        Dialog<CreateAuctionRequest> dialog = new Dialog<>();
        dialog.setTitle("Tạo phiên đấu giá");
        dialog.setHeaderText("Sản phẩm: " + selected.getName());

        DialogPane pane = dialog.getDialogPane();
        // Áp dụng stylesheet cho dialog để đồng bộ giao diện
        java.net.URL css = getClass().getResource("/com/bt/styles/app.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(10, 4, 4, 4));

        Label info = new Label("Phiên sẽ bắt đầu NGAY khi bạn xác nhận.");
        info.getStyleClass().add("subtitle");
        grid.add(info, 0, 0, 2, 1);

        Label hoursLabel = new Label("Thời lượng (giờ):");
        hoursLabel.getStyleClass().add("label-form");
        TextField hoursField = new TextField("12");
        hoursField.setPrefWidth(140);
        grid.add(hoursLabel, 0, 1);
        grid.add(hoursField, 1, 1);

        Label hint = new Label("Tối thiểu 1 phút (~0.02 giờ). Vd: 1, 2, 12, 24, 48...");
        hint.getStyleClass().add("muted");
        grid.add(hint, 0, 2, 2, 1);

        pane.setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String txt = hoursField.getText() == null ? "" : hoursField.getText().trim();
            double hours;
            try {
                hours = Double.parseDouble(txt);
            } catch (NumberFormatException nfe) {
                showAlert("Số giờ không hợp lệ");
                return null;
            }
            if (hours <= 0 || hours > 24 * 30) {
                showAlert("Thời lượng phải > 0 và ≤ 720 giờ (30 ngày)");
                return null;
            }
            LocalDateTime now = LocalDateTime.now().withNano(0);
            LocalDateTime end = now.plusSeconds(Math.round(hours * 3600));
            // Đảm bảo cách ≥ 1 phút
            if (java.time.Duration.between(now, end).toMinutes() < 1) {
                showAlert("Phiên phải kéo dài ít nhất 1 phút");
                return null;
            }
            return new CreateAuctionRequest(selected.getItemId(), now, end);
        });

        dialog.showAndWait().ifPresent(req -> sendCreateAuction(req));
    }

    private void showAlert(String msg) {
        Dialogs.error("Dữ liệu không hợp lệ", msg);
    }

    private void sendCreateAuction(CreateAuctionRequest req) {
        leftStatusLabel.setText("Đang tạo phiên...");
        new Thread(() -> {
            try {
                AuctionDto created = client.createAuction(req);
                Platform.runLater(() -> {
                    leftStatusLabel.setText("Đã tạo phiên id=" + created.getAuctionId()
                            + " status=" + created.getStatus());
                    refreshItems();
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> showError(leftStatusLabel, ex));
            }
        }, "create-auction").start();
    }

    @FXML
    public void back() {
        SceneManager.get().switchTo("dashboard");
    }

    private void showError(Label label, AuctionClientException ex) {
        label.setText(ex.getMessage());
        Dialogs.error(ErrorMessages.title(ex.getCode()), ex.getMessage());
    }

    private static String requireText(TextField field, String name) {
        if (field == null || field.getText() == null || field.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Thiếu " + name);
        }
        return field.getText().trim();
    }
}
