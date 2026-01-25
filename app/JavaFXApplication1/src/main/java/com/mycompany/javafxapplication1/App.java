package com.mycompany.javafxapplication1;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import com.mycompany.javafxapplication1.AppLogger;

/**
 * JavaFX App
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        //Ensure MySQL table exists
        MySQLDB mysql = new MySQLDB();
        mysql.ensureSchema();
        AppLogger.info("APPLICATION STARTED");
        //Load login screen
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("primary.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 640, 480);
        stage.setScene(scene);
        stage.setTitle("Primary View");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}