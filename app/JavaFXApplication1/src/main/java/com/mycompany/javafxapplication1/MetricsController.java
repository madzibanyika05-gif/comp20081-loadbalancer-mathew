/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.event.ActionEvent;
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

        metricsArea.setText(Metrics.getSummary());
    }

    @FXML
    private void goBack(ActionEvent event) {
        App.setRoot("secondary");
    }
}
