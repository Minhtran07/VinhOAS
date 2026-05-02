package com.bt.client.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;

import java.net.URL;

/**
 * Helper hiển thị Alert dialog an toàn từ MỌI thread.
 *
 * Quan trọng: JavaFX bắt buộc Alert API (constructor + show + showAndWait)
 * phải chạy trên FX Application Thread. Gọi từ thread phụ → throw
 * IllegalStateException → app đơ.
 *
 * Class này dispatch lên FX thread bằng {@link Platform#runLater} —
 * caller không cần nhớ phải gọi từ thread nào.
 *
 * Cách dùng:
 *   Dialogs.error("Tiêu đề", "Nội dung");   // hiển thị ngay
 *   Dialogs.info(...);
 *   Dialogs.warn(...);
 */
public final class Dialogs {

    private static final String STYLESHEET = "/com/bt/styles/app.css";

    private Dialogs() {}

    public static void error(String header, String message) {
        showLater(Alert.AlertType.ERROR, "Lỗi", header, message);
    }

    public static void info(String header, String message) {
        showLater(Alert.AlertType.INFORMATION, "Thông báo", header, message);
    }

    public static void warn(String header, String message) {
        showLater(Alert.AlertType.WARNING, "Cảnh báo", header, message);
    }

    private static void showLater(Alert.AlertType type, String title,
                                  String header, String message) {
        Runnable task = () -> {
            Alert a = new Alert(type, message);
            a.setTitle(title);
            a.setHeaderText(header);
            applyStyle(a.getDialogPane());
            a.show();
        };
        if (Platform.isFxApplicationThread()) task.run();
        else Platform.runLater(task);
    }

    public static void applyStyle(DialogPane pane) {
        URL url = Dialogs.class.getResource(STYLESHEET);
        if (url != null && !pane.getStylesheets().contains(url.toExternalForm())) {
            pane.getStylesheets().add(url.toExternalForm());
        }
    }
}
