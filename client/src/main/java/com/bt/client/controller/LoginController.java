package com.bt.client.controller;

import com.bt.client.net.AuctionClient;
import com.bt.client.net.AuctionClientException;
import com.bt.client.session.Session;
import com.bt.client.ui.SceneManager;
import com.bt.client.util.Dialogs;
import com.bt.client.util.ErrorMessages;
import com.bt.shared.protocol.dto.LoginResponse;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    private final AuctionClient client = new AuctionClient();

    @FXML
    private void handleLogin() {
        String u = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String p = passwordField.getText() == null ? "" : passwordField.getText();
        if (u.isEmpty()) {
            statusLabel.setText("Vui lòng nhập username");
            usernameField.requestFocus();
            return;
        }
        if (p.isEmpty()) {
            statusLabel.setText("Vui lòng nhập password");
            passwordField.requestFocus();
            return;
        }
        statusLabel.setText("Đang đăng nhập...");

        new Thread(() -> {
            try {
                LoginResponse resp = client.login(u, p);
                Session.get().setFromLogin(resp);
                // Cho phép reconnect tự re-login khi mạng rớt rồi phục hồi.
                Session.get().setReplayPassword(p);
                Platform.runLater(() -> {
                    statusLabel.setText("Xin chào " + resp.getUsername());
                    SceneManager.get().switchTo("dashboard");
                });
            } catch (AuctionClientException ex) {
                Platform.runLater(() -> {
                    statusLabel.setText(ex.getMessage());
                    Dialogs.error(ErrorMessages.title(ex.getCode()), ex.getMessage());
                });
            }
        }, "login-task").start();
    }

    @FXML
    private void switchToRegister() {
        SceneManager.get().switchTo("register");
    }
}
