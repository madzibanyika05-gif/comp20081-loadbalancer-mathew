package com.mycompany.javafxapplication1;

import com.mycompany.javafxapplication1.AppLogger;
import com.mycompany.javafxapplication1.Session;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import java.util.HashMap;
import java.util.Map;

public class PrimaryController {
    
    private static final int MAX_ATTEMPTS = 5;//log in attempts
    private static final long BLOCK_WINDOW_MS = 60_000; // 1 minute

    private static final Map<String, Integer> attempts = new HashMap<>();
    private static final Map<String, Long> blockedUntil = new HashMap<>();

    @FXML
    private Button registerBtn;

    @FXML
    private TextField userTextField;

    @FXML
    private PasswordField passPasswordField;

    @FXML
    private void registerBtnHandler(ActionEvent event) {
        Stage secondaryStage = new Stage();
        Stage primaryStage = (Stage) registerBtn.getScene().getWindow();
        MySQLDB myObj = new MySQLDB();
        AppLogger.info("OPEN REGISTER SCREEN");

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("register.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Register a new User");
            secondaryStage.show();
            primaryStage.close();
        } catch (Exception e) {
            AppLogger.error("OPEN REGISTER SCREEN ERROR", e);
            e.printStackTrace();
        }
    }

    private void dialogue(String headerMsg, String contentMsg) {
        Stage secondaryStage = new Stage();
        Group root = new Group();
        Scene scene = new Scene(root, 300, 300, Color.DARKGRAY);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Confirmation Dialog");
        alert.setHeaderText(headerMsg);
        alert.setContentText(contentMsg);

        Optional<ButtonType> result = alert.showAndWait();
    }

    @FXML
    private void switchToSecondary() {
        String username = userTextField.getText().trim();
        Long blocked = blockedUntil.get(username);
        if (blocked != null && System.currentTimeMillis() < blocked) {
            AppLogger.warn("LOGIN BLOCKED user=" + username);
            return;
        }
        Stage secondaryStage = new Stage();
        Stage primaryStage = (Stage) registerBtn.getScene().getWindow();

        try {
            MySQLDB myObj = new MySQLDB();

            String user = userTextField.getText().trim();
            String pass = passPasswordField.getText();
            AppLogger.info("LOGIN ATTEMPT user=" + user);
            
            String role = myObj.getRoleIfValidLogin(user, pass);

            if (role != null) {

                Session.login(user, role);
                attempts.remove(user);//resets reate limit when logged in success
                blockedUntil.remove(user);
                AppLogger.info("LOGIN success user=" + Session.getUsername() + " role=" + Session.getRole());

                FXMLLoader loader = new FXMLLoader();
                loader.setLocation(getClass().getResource("secondary.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 640, 480);
                secondaryStage.setScene(scene);

                SecondaryController controller = loader.getController();
                controller.initialise(user);

                secondaryStage.setTitle("Show Users");
                secondaryStage.setUserData("some data sent from Primary Controller");
                secondaryStage.show();
                primaryStage.close();

            } else {
                AppLogger.warn("LOGIN failed user=" + user);
                //rate limitting logic
                attempts.put(user, attempts.getOrDefault(user, 0) + 1);
                if (attempts.get(user) >= MAX_ATTEMPTS) {
                    blockedUntil.put(user, System.currentTimeMillis() + BLOCK_WINDOW_MS);
                    attempts.remove(user);
                    AppLogger.warn("LOGIN RATE-LIMIT user=" + user);
                }
                dialogue("Invalid User Name / Password", "Please try again!");
            }

        } catch (Exception e) {
            AppLogger.error("LOGIN ERROR", e);
            e.printStackTrace();
        }
    }
}
