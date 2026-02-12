/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import java.io.IOException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
/**
 *
 * @author ntu-user
 */
public class LogsController {

    @FXML
    private TextArea logsArea;

    @FXML
    public void initialize() {
        // admin-only
        if (!Session.isAdmin()) {
            logsArea.setText("Access denied.");
            return;
        }

        try {
            // OPTION A (recommended): read your log file
            logsArea.setText(AppLogger.readLogTail(500)); // last 500 lines
        } catch (Exception e) {
            logsArea.setText("ERROR: could not load logs.\n" + e.getMessage());
        }
    }

    @FXML
    private void goBack(ActionEvent event) {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) logsArea.getScene().getWindow();
            javafx.fxml.FXMLLoader loader =
                    new javafx.fxml.FXMLLoader(getClass().getResource("secondary.fxml"));
            javafx.scene.Parent root = loader.load();

            SecondaryController controller = loader.getController();
            controller.initialise(Session.getUsername());
            stage.setScene(new javafx.scene.Scene(root, 900, 650));
            stage.setTitle("Show Users");

        } catch (IOException e) {
            logsArea.setText("ERROR: couldn't return.\n" + e.getMessage());
        }
    }
}
