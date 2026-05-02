package com.bt.client.controller;

import com.bt.client.ui.SceneManager;

import javafx.fxml.FXML;

public class AdminController {

    @FXML
    private void back() {
        SceneManager.get().switchTo("dashboard");
    }
}
