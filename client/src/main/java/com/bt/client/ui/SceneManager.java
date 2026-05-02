package com.bt.client.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Quản lý chuyển scene tập trung. Mọi root được load đều có stylesheet
 * chung {@code styles/app.css} áp dụng tự động.
 */
public class SceneManager {

    private static SceneManager instance;
    private static final String STYLESHEET_PATH = "/com/bt/styles/app.css";

    private Stage primaryStage;
    private Scene scene;

    private SceneManager() {}

    public static SceneManager get() {
        if (instance == null) instance = new SceneManager();
        return instance;
    }

    public void init(Stage stage, String firstFxml) throws IOException {
        this.primaryStage = stage;
        Parent root = loadFxml(firstFxml);
        this.scene = new Scene(root, 1280, 800);
        applyStylesheet(scene);
        stage.setScene(scene);
        stage.setTitle("Online Auction System");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.show();
    }

    public void switchTo(String fxmlName) {
        // Buộc thực thi trên FX thread (button.onAction đã chạy FX thread,
        // nhưng listener selection có thể chạy thread khác)
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(() -> switchTo(fxmlName));
            return;
        }
        try {
            System.out.println("[SceneManager] switchTo " + fxmlName + " ...");
            Parent root = loadFxml(fxmlName);
            if (scene == null) {
                throw new IllegalStateException(
                        "Scene chưa được init() — gọi SceneManager.init() trước");
            }
            scene.setRoot(root);
            System.out.println("[SceneManager] switchTo " + fxmlName + " DONE");
        } catch (Throwable e) {
            // Bắt cả RuntimeException (lỗi load FXML, controller init,...)
            // KHÔNG để app im lặng — show dialog cho user thấy
            System.err.println("[SceneManager] switchTo(" + fxmlName + ") FAIL:");
            e.printStackTrace();
            // Lấy stack trace ngắn — cause cuối cùng + 5 dòng top frame
            Throwable rootCause = e;
            while (rootCause.getCause() != null) rootCause = rootCause.getCause();
            StringBuilder sb = new StringBuilder();
            sb.append(rootCause.getClass().getSimpleName()).append(": ")
                    .append(rootCause.getMessage()).append("\n\nStack:\n");
            StackTraceElement[] trace = rootCause.getStackTrace();
            for (int i = 0; i < Math.min(8, trace.length); i++) {
                sb.append("  at ").append(trace[i]).append("\n");
            }
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Lỗi mở màn hình");
                alert.setHeaderText("Không thể chuyển sang " + fxmlName);
                javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(sb.toString());
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefRowCount(12);
                ta.setPrefColumnCount(80);
                alert.getDialogPane().setContent(ta);
                alert.showAndWait();
            } catch (Exception ignored) {}
        }
    }

    private void applyStylesheet(Scene scene) {
        URL css = getClass().getResource(STYLESHEET_PATH);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            System.err.println("[SceneManager] Không tìm thấy " + STYLESHEET_PATH);
        }
    }

    private Parent loadFxml(String name) throws IOException {
        URL url = getClass().getResource("/com/bt/" + name + ".fxml");
        if (url == null) throw new IOException("FXML không tìm thấy: " + name);
        // Dùng instance loader thay vì static FXMLLoader.load() — instance
        // loader cho phép setLocation và sẽ propagate exception trong
        // controller.initialize() rõ ràng hơn (kèm cause).
        FXMLLoader loader = new FXMLLoader(url);
        try {
            return loader.load();
        } catch (RuntimeException re) {
            // FXMLLoader có thể wrap controller initialize errors trong
            // RuntimeException — log + rethrow để switchTo() catch và show alert.
            System.err.println("[SceneManager] FXML load runtime error in " + name
                    + ": " + re.getMessage());
            re.printStackTrace();
            throw re;
        }
    }

    public Stage getStage() { return primaryStage; }
}
