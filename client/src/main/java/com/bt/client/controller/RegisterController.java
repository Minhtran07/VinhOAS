package com.bt.client.controller;

import com.bt.client.net.AuctionClient;
import com.bt.client.net.AuctionClientException;
import com.bt.client.ui.SceneManager;
import com.bt.client.util.Dialogs;
import com.bt.client.util.ErrorMessages;
import com.bt.shared.UserRole;
import com.bt.shared.protocol.dto.RegisterRequest;
import com.bt.shared.protocol.dto.RegisterResponse;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private ChoiceBox<UserRole> roleChoice;
    @FXML private Label statusLabel;

    private final AuctionClient client = new AuctionClient();

    @FXML
    public void initialize() {
        // Chỉ cho đăng ký BIDDER hoặc SELLER
        roleChoice.setItems(FXCollections.observableArrayList(UserRole.BIDDER, UserRole.SELLER));
        roleChoice.setValue(UserRole.BIDDER);
    }

    @FXML
    private void handleRegister() {
        // Client-side validation: chặn request rỗng/sai format trước khi
        // tốn round-trip lên server. Server vẫn validate lại để chống forge.
        String username = safe(usernameField.getText());
        String email = safe(emailField.getText());
        String password = safe(passwordField.getText());
        UserRole role = roleChoice.getValue();

        if (username.isEmpty() || username.length() < 3 || username.length() > 30) {
            statusLabel.setText("Username phải 3-30 ký tự");
            usernameField.requestFocus();
            return;
        }
        if (!username.matches("^[a-zA-Z0-9_.-]+$")) {
            statusLabel.setText("Username chỉ chứa chữ, số, _ . -");
            usernameField.requestFocus();
            return;
        }
        if (email.isEmpty() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            statusLabel.setText("Email không hợp lệ");
            emailField.requestFocus();
            return;
        }
        if (password.length() < 6) {
            statusLabel.setText("Password tối thiểu 6 ký tự");
            passwordField.requestFocus();
            return;
        }
        if (role == null) {
            statusLabel.setText("Chọn role trước khi đăng ký");
            return;
        }

        RegisterRequest req = new RegisterRequest(username, email, password, role);
        statusLabel.setText("Đang đăng ký...");

        new Thread(() -> {
            try {
                RegisterResponse resp = client.register(req);
                Platform.runLater(() -> {
                    Dialogs.info("Tạo tài khoản thành công",
                            "Đã tạo tài khoản " + resp.getUsername()
                                    + ". Vui lòng đăng nhập để tiếp tục.");
                    SceneManager.get().switchTo("login");
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> {
                    statusLabel.setText(ex.getMessage());
                    Dialogs.error(ErrorMessages.title(ex.getCode()), ex.getMessage());
                });
            }
        }, "register-task").start();
    }

    @FXML
    private void backToLogin() {
        SceneManager.get().switchTo("login");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
