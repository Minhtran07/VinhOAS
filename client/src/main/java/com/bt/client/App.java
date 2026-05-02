package com.bt.client;

import com.bt.client.config.ClientConfig;
import com.bt.client.net.ServerConnection;
import com.bt.client.ui.SceneManager;

import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Entry point. Khởi tạo SceneManager với màn hình Login, kết nối server bg.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        SceneManager.get().init(stage, "login");

        new Thread(() -> {
            try {
                ServerConnection.getInstance()
                        .connect(ClientConfig.serverHost(), ClientConfig.serverPort());
                System.out.println("[Client] Connected to "
                        + ClientConfig.serverHost() + ":" + ClientConfig.serverPort());
            } catch (IOException ex) {
                System.err.println("[Client] Không kết nối được server: " + ex.getMessage());
            }
        }, "server-connector").start();
    }

    @Override
    public void stop() {
        ServerConnection.getInstance().shutdown();
    }

    public static void main(String[] args) {
        launch();
    }
}
