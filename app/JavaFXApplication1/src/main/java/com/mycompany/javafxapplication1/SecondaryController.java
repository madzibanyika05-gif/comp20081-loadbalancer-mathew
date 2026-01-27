package com.mycompany.javafxapplication1;

import com.mycompany.javafxapplication1.MySQLDB;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import com.mycompany.javafxapplication1.AppLogger;
import com.mycompany.javafxapplication1.Session;



public class SecondaryController {
    
    @FXML
    private TextField userTextField;
    
    @FXML
    private TableView dataTableView;

    @FXML
    private Button secondaryButton;
    
    @FXML
    private Button refreshBtn;
    
    @FXML
    private TextField customTextField;
    
    @FXML
    private TextField fileNameField;
    
    @FXML
    private javafx.scene.control.ListView<String> filesListView;
    
    @FXML
    private Button deleteUserBtn;
    
    @FXML
    private void RefreshBtnHandler(ActionEvent event){
        try {
            String username = Session.getUsername();
            String content;

            try {
                content = FileService.readTextFile(username, "custom.txt");
            } catch (IOException ex) {
                content = FileService.readTextFile(username, "welcome.txt");
            }

            customTextField.setText(content);
            refreshFileList();
        } catch (IOException e) {
            e.printStackTrace();
            customTextField.setText("ERROR: couldn't read file");
        }
    }
    
    @FXML
    private void deleteCustomFile() {
        try {
            String username = Session.getUsername();

            String file = fileNameField.getText().trim();
            if (file.isEmpty()) {
                file = filesListView.getSelectionModel().getSelectedItem();
            }
            if (file == null || file.trim().isEmpty()) return;

            FileService.deleteFile(username, file);
            customTextField.setText("");
            fileNameField.setText("");
            refreshFileList();
        } catch (IOException e) {
            e.printStackTrace();
            customTextField.setText("ERROR: could not delete file");
        }
    }
    
    @FXML
    private void saveCustomData(ActionEvent event) {
        try {
            String username = Session.getUsername();
            String file = fileNameField.getText().trim();

            if (file.isEmpty()) {
                file = "custom.txt";
                fileNameField.setText(file);
            }

            FileService.writeTextFile(username, file, customTextField.getText());
            refreshFileList();
        } catch (IOException e) {
            e.printStackTrace();
            customTextField.setText("ERROR: could not save data");
        }
    }
    
    @FXML
    private void refreshFileList() {
        try {
            String username = Session.getUsername();
            java.util.List<String> files = FileService.listUserFiles(username); // we’ll add this if missing
            filesListView.getItems().setAll(files);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    private void loadSelectedFile() {
        try {
            String username = Session.getUsername();
            String file = filesListView.getSelectionModel().getSelectedItem();
            if (file == null) return;

            String content = FileService.readTextFile(username, file);
            fileNameField.setText(file);
            customTextField.setText(content);
        } catch (IOException e) {
            e.printStackTrace();
            customTextField.setText("ERROR: couldn't load file");
        }
    }
        
    @FXML
    private void switchToPrimary(){

        AppLogger.info("LOGOUT user=" + Session.getUsername());
        Session.logout();

        Stage secondaryStage = new Stage();
        Stage primaryStage = (Stage) secondaryButton.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("primary.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Login");
            secondaryStage.show();
            primaryStage.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    private void deleteSelectedUser(ActionEvent event) {
        if (!Session.isAdmin()) {
            AppLogger.warn("ACCESS DENIED delete user attempt by " + Session.getUsername());
            return;
        }
        
        User selected = (User) dataTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AppLogger.warn("DELETE USER clicked but nothing selected.");
            return;
        }

        String target = selected.getUser();

        // don't let admin delete themselves (avoids locking yourself out)
        if (target.equalsIgnoreCase(Session.getUsername())) {
            AppLogger.warn("ADMIN tried to delete self: " + target);
            return;
        }

        MySQLDB db = new MySQLDB();
        boolean ok = db.deleteUser(target);

        if (ok) {
            AppLogger.info("USER DELETED: " + target + " by " + Session.getUsername());
            // refresh table
            ObservableList<User> data = db.getDataFromMySQL();
            dataTableView.setItems(data);
        } else {
            AppLogger.warn("DELETE USER failed for: " + target);
        }
    }
    
    private void applyRolePermissions() {
        boolean admin = Session.isAdmin();

    //data table shws on admin
        dataTableView.setVisible(admin);
        dataTableView.setManaged(admin);

    //user delte shows on admin
        deleteUserBtn.setVisible(admin);
        deleteUserBtn.setManaged(admin);
    }
    
    public void initialise(String username) {
        if (!Session.isLoggedIn()) {
            AppLogger.warn("ACCESS BLOCKED: unauthenticated access attempt");
            switchToPrimary();
            return;
        }
        applyRolePermissions();
        userTextField.setText(username);
        try {
            if (!FileService.fileExists(username, "welcome.txt")) {// only create welcome.txt if it doesn't already exist
                FileService.writeTextFile(
                    username,
                    "welcome.txt",
                    "Welcome " + username + "! Your storage is working."
                );
            }

            String content;
            if (FileService.fileExists(username, "custom.txt")) {
                content = FileService.readTextFile(username, "custom.txt");
            } else {
                content = FileService.readTextFile(username, "welcome.txt");
            }

            customTextField.setText(content);
            refreshFileList();

        } catch (IOException e) {
            e.printStackTrace();
            customTextField.setText("ERROR: couldn't read/write file");
        }

        
        System.out.print("Session.getUsername(): " + Session.getUsername()); //proof session is set
        //role based access
        if (!Session.isAdmin()) {
            AppLogger.warn("ACCESS: non-admin tried to view user table. user=" 
                + Session.getUsername() + " role=" + Session.getRole());
            return;
        }
        
        MySQLDB myObj = new MySQLDB();
        ObservableList<User> data = myObj.getDataFromMySQL();
        
        System.out.println("MySQL users loaded: " + data.size());
        for (User u : data) {
            System.out.println(" - " + u.getUser());
        }

        dataTableView.getColumns().clear();

        TableColumn<User, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("user"));

        TableColumn<User, String> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(new PropertyValueFactory<>("pass"));

        TableColumn<User, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));

        dataTableView.setItems(data);
        dataTableView.getColumns().addAll(userCol, passCol, roleCol);
    }
}
