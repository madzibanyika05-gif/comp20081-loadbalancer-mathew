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
    private void RefreshBtnHandler(ActionEvent event){
        try {
            String username = Session.getUsername();
            String content = FileService.readTextFile(username, "welcome.txt");
            customTextField.setText(content);
        } catch (IOException e) {
            e.printStackTrace();
            customTextField.setText("ERROR: couldn't read file");
        }
    }
    
    @FXML
    private void deleteCustomFile() {
        try {
            String username = Session.getUsername();
            FileService.deleteFile(username, "custom.txt");
            customTextField.setText("");
        } catch (IOException e) {
            e.printStackTrace();
            customTextField.setText("ERROR: could not delete file");
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
        userTextField.setText(username); //show username on the screen
        try {
    FileService.writeTextFile(
        username,
        "welcome.txt",
        "Welcome " + username + "! Your storage is working."
    );
    String content = FileService.readTextFile(username, "welcome.txt");
    customTextField.setText(content);//content
    System.out.println(content);
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
