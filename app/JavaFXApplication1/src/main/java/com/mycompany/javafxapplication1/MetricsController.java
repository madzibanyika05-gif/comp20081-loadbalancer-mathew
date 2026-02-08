/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
/**
 *
 * @author ntu-user
 */
public class MetricsController {

    @FXML
    private TextArea metricsArea;

    @FXML
    public void initialize() {
        if (!Session.isAdmin()) {
            metricsArea.setText("Access denied.");
            return;
        }

        metricsArea.setText(Metrics.snapshot());
    }

    @FXML
    private void goBack(ActionEvent event) {
        try {
            Stage stage = (Stage) metricsArea.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("secondary.fxml"));
            Parent root = loader.load();

            SecondaryController controller = loader.getController();
        controller.initialise(Session.getUsername());

            stage.setScene(new Scene(root, 640, 480));
            stage.setTitle("Show Users");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
