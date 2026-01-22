package com.mycompany.javafxapplication1;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    public void initialise(String username) {
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
        DB myObj = new DB();
        ObservableList<User> data;
        try {
            data = myObj.getDataFromTable();
            dataTableView.getColumns().clear();
            TableColumn user = new TableColumn("User");
        user.setCellValueFactory(
        new PropertyValueFactory<>("user"));

        TableColumn pass = new TableColumn("Pass");
        pass.setCellValueFactory(
            new PropertyValueFactory<>("pass"));
        dataTableView.setItems(data);
        dataTableView.getColumns().addAll(user, pass);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SecondaryController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
