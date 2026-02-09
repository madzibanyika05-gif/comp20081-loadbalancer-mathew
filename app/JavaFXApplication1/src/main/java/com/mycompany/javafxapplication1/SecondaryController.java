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
import javafx.scene.control.TextArea;
import javafx.scene.control.Label;



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
    private TextField shareUserField;
    
    @FXML
    private javafx.scene.control.ChoiceBox<String> permChoice;
    
    @FXML
    private Button metricsBtn;
    
    @FXML
    private Button logsBtn;

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
            FileRef ref = getSelectedFileRef();

            String file = fileNameField.getText().trim();
            if (file.isEmpty()) {
                file = filesListView.getSelectionModel().getSelectedItem();
            }
            if (ref != null && !ref.owner.equalsIgnoreCase(username)) {//deleting shared files
                AppLogger.warn("ACL DELETE denied user=" + username + " owner=" + ref.owner + " file=" + ref.filename);
                customTextField.setText("ACCESS DENIED (DELETE)");
                return;
            }
            if (file == null || file.trim().isEmpty()) return;

            String targetOwner = (ref != null) ? ref.owner : username;
            FileService.deleteFile(targetOwner, file);
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
            //select or type which file to save to
            String typed = fileNameField.getText().trim();
            FileRef selectedRef = getSelectedFileRef();

            FileRef ref;
            if (selectedRef != null) {//if a file is selected in the list, save back to THAT file (including shared files)
                String nameToSave = typed.isEmpty() ? selectedRef.filename : typed;
                ref = new FileRef(selectedRef.owner, nameToSave, selectedRef.perm);
            }
            //or save to your own storage using whatever they typed (or default)
            else if (!typed.isEmpty()) {
                ref = new FileRef(username, typed, null);
            } else {
                ref = new FileRef(username, "custom.txt", null);
                fileNameField.setText("custom.txt");
            }
            
            if (!ref.owner.equalsIgnoreCase(username)) {//enforce write permission when saving someone elses file
                MySQLDB db = new MySQLDB();
                if (!db.hasPermission(ref.owner, ref.filename, username, "WRITE")) {
                    AppLogger.warn("ACL WRITE denied user=" + username + " owner=" + ref.owner + " file=" + ref.filename);
                    customTextField.setText("ACCESS DENIED (WRITE)");
                    return;
                }
            }

            FileService.writeTextFile(ref.owner, ref.filename, customTextField.getText());
            AppLogger.info("FILE SAVED editor=" + username + " owner=" + ref.owner + " file=" + ref.filename);
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
            fileIndex.clear();
            //for users own file
            java.util.List<String> own = FileService.listUserFiles(username);
            for (String f : own) {
            String display = f;
            fileIndex.put(display, new FileRef(username, f, null));
            }
            //shared files from mysql table
            MySQLDB db = new MySQLDB();
            ObservableList<MySQLDB.SharedFile> shared = db.getSharedFilesFor(username);
            for (MySQLDB.SharedFile s : shared) {
            String display = s.getOwner() + "::" + s.getFilename() + " (" + s.getPerm() + ")";
            fileIndex.put(display, new FileRef(s.getOwner(), s.getFilename(), s.getPerm()));
            }

            filesListView.getItems().setAll(fileIndex.keySet());
            filesListView.getItems().sort(String::compareToIgnoreCase);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 
    @FXML
    private void loadSelectedFile() {
        try {
            String username = Session.getUsername();
            FileRef ref = getSelectedFileRef();
            if (ref == null) return;
            //enforces read permision if file isnt theirs
            if (!ref.owner.equalsIgnoreCase(username)) {
                MySQLDB db = new MySQLDB();
                if (!db.hasPermission(ref.owner, ref.filename, username, "READ")) {
                    AppLogger.warn("ACL READ denied user=" + username + " owner=" + ref.owner + " file=" + ref.filename);
                    customTextField.setText("ACCESS DENIED (READ)");
                    return;
                }
            }
            String content = FileService.readTextFile(ref.owner, ref.filename);
            fileNameField.setText(ref.filename);
            customTextField.setText(content);

            AppLogger.info("FILE LOADED viewer=" + username + " owner=" + ref.owner + " file=" + ref.filename);
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
    
    @FXML
    private void shareSelectedFile() {
        try {
            String owner = Session.getUsername();
            FileRef ref = getSelectedFileRef();
            if (ref == null) {
                AppLogger.warn("SHARE clicked but no file selected owner=" + owner);
                return;
            }
            if (!ref.owner.equalsIgnoreCase(owner)) {//only owner can share
                AppLogger.warn("SHARE denied (not owner) user=" + owner + " owner=" + ref.owner + " file=" + ref.filename);
                customTextField.setText("ACCESS DENIED (SHARE)");
                return;
            }
            
            String grantee = shareUserField.getText().trim();
            if (grantee.isBlank()) return;
            String perm = permChoice.getValue(); //read or write
            if (perm == null) perm = "READ";
            MySQLDB db = new MySQLDB();
            boolean ok = db.grantFilePermission(owner, ref.filename, grantee, perm);

            if (ok) {
                AppLogger.info("ACL GRANTED owner=" + owner + " file=" + ref.filename + " grantee=" + grantee + " perm=" + perm);
            } else {
                AppLogger.warn("ACL GRANT FAILED owner=" + owner + " file=" + ref.filename + " grantee=" + grantee);
            }

        } catch (Exception e) {
            AppLogger.error("SHARE ERROR", e);
            e.printStackTrace();
        }
    }

    @FXML
    private void revokeSelectedFile() {
        try {
            String owner = Session.getUsername();
            FileRef ref = getSelectedFileRef();
            if (ref == null) return;
            if (!ref.owner.equalsIgnoreCase(owner)) {//only owner can revoke
                AppLogger.warn("REVOKE denied (not owner) user=" + owner + " owner=" + ref.owner + " file=" + ref.filename);
                customTextField.setText("ACCESS DENIED (REVOKE)");
                return;
            }
            String grantee = shareUserField.getText().trim();
            if (grantee.isBlank()) return;
            MySQLDB db = new MySQLDB();
            boolean ok = db.revokeFilePermission(owner, ref.filename, grantee);

            if (ok) {
                AppLogger.info("ACL REVOKED owner=" + owner + " file=" + ref.filename + " grantee=" + grantee);
            } else {
                AppLogger.warn("ACL REVOKE FAILED owner=" + owner + " file=" + ref.filename + " grantee=" + grantee);
            }

        } catch (Exception e) {
            AppLogger.error("REVOKE ERROR", e);
            e.printStackTrace();
        }
    }
    
    @FXML
    private void openMetrics() {
        if (!Session.isAdmin()) return;

        try {
            Stage stage = (Stage) userTextField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("metrics.fxml"));
            Parent root = loader.load();
            stage.setScene(new Scene(root, 640, 480));
            stage.setTitle("Performance Metrics");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    private void openLogs() {
        if (!Session.isAdmin()) return;
        try {
            Stage stage = (Stage) userTextField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("logs.fxml"));
            Parent root = loader.load();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Logging History");
        } catch (Exception e) {
            e.printStackTrace();
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
        
        if (metricsBtn != null) {
            metricsBtn.setVisible(admin);
            metricsBtn.setManaged(admin);
        
        if (logsBtn != null) {
            logsBtn.setVisible(admin);
            logsBtn.setManaged(admin);
        }
    }
    
    private static class FileRef {
        String owner;
        String filename;
        String perm; //null for own files and read and write shared files

        FileRef(String owner, String filename, String perm) {
            this.owner = owner;
            this.filename = filename;
            this.perm = perm;
        }
    }

    private final java.util.Map<String, FileRef> fileIndex = new java.util.HashMap<>();

    private FileRef getSelectedFileRef() {
        String key = filesListView.getSelectionModel().getSelectedItem();
        if (key == null) return null;
        return fileIndex.get(key);
    }
    
    public void initialise(String username) {
        if (!Session.isLoggedIn()) {
            AppLogger.warn("ACCESS BLOCKED: unauthenticated access attempt");
            switchToPrimary();
            return;
        }
        applyRolePermissions();
        permChoice.getItems().setAll("READ", "WRITE");
        permChoice.setValue("READ");
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
