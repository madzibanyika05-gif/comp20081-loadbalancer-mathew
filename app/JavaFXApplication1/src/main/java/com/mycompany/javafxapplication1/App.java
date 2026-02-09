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
    private static Scene scene;
    @Override
    public void start(Stage stage) throws IOException {

        //Ensure MySQL table exists
        MySQLDB mysql = new MySQLDB();
        mysql.ensureSchema();
        LocalSQLiteDB.ensureSchema();
        LocalSQLiteDB.SessionData saved = LocalSQLiteDB.loadSession(); //session restore
        if (saved != null) {
            Session.login(saved.username(), saved.role());
            AppLogger.info(
                "SESSION RESTORED user=" + saved.username() + " role=" + saved.role()
            );
        }

        AppLogger.info("APPLICATION STARTED");//Load login screen

        FXMLLoader loader = new FXMLLoader();
        Parent root;

        if (saved != null) {//put restored session into in memory Session
            Session.login(saved.username(), saved.role());
            AppLogger.info("SESSION RESTORED user=" + saved.username() + " role=" + saved.role());

            loader.setLocation(getClass().getResource("secondary.fxml"));//go straight to secondary screen
            root = loader.load();
            SecondaryController controller = loader.getController();
            controller.initialise(saved.username());

            stage.setTitle("Show Users");
        } else {//no session -> normal login screen
            loader.setLocation(getClass().getResource("primary.fxml"));
            root = loader.load();
            stage.setTitle("Primary View");
        }

        scene = new Scene(root, 640, 480);
        stage.setScene(scene);
        stage.show();
    }
    
    public static void setRoot(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        Parent root = loader.load();
        if ("secondary".equals(fxml) && Session.isLoggedIn()) {
            SecondaryController controller = loader.getController();
            controller.initialise(Session.getUsername());
        }

        scene.setRoot(root);
    }

    public static void main(String[] args) {
        launch();
    }
}