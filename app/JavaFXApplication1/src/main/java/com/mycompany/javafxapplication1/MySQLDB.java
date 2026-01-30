/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

/**
 *
 * @author ntu-user
 */
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MySQLDB {

    private static final String URL = "jdbc:mysql://comp20081-mysql:3306/comp20081";
    private static final String USER = "comp20081";
    private static final String PASS = "comp20081";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public boolean validateUser(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    return PasswordUtil.verifyPassword(password, storedHash);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
        
    public void addDataToDB(String username, String passwordHash) {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, 'USER')";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void ensureSchema() {
            String sql =
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "role VARCHAR(10) NOT NULL DEFAULT 'USER', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

            try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

                stmt.execute(sql);
                
                String aclSql =
                    "CREATE TABLE IF NOT EXISTS file_permissions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "owner VARCHAR(50) NOT NULL, " +
                    "filename VARCHAR(255) NOT NULL, " +
                    "grantee VARCHAR(50) NOT NULL, " +
                    "perm VARCHAR(10) NOT NULL, " + //read and write
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uniq_perm (owner, filename, grantee)" +
                    ")";

                stmt.execute(aclSql);

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    
    public static void main(String[] args) {
        try {
            MySQLDB db = new MySQLDB();
            db.getConnection();
            System.out.println("MySQL connected OK");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public ObservableList<User> getDataFromMySQL() {
        ObservableList<User> data = FXCollections.observableArrayList();
        String sql = "SELECT username, password_hash, role FROM users ORDER BY id";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                data.add(new User(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return data;
    }
    
    public String getRoleIfValidLogin(String username, String passwordPlain) {
        String sql = "SELECT password_hash, role FROM users WHERE username = ? LIMIT 1";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                String role = rs.getString("role");   // USER / ADMIN

                if (PasswordUtil.verifyPassword(passwordPlain, storedHash)) {
                    return role; //login ok, return role
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null; //login failed
    }
    
    public boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    //file to handle permision
    public static class SharedFile {
        private final String owner;
        private final String filename;
        private final String perm;

        public SharedFile(String owner, String filename, String perm) {
            this.owner = owner;
            this.filename = filename;
            this.perm = perm;
        }

        public String getOwner() { return owner; }
        public String getFilename() { return filename; }
        public String getPerm() { return perm; }
    }

    public boolean grantFilePermission(String owner, String filename, String grantee, String perm) {
        String sql =
            "INSERT INTO file_permissions(owner, filename, grantee, perm) " +
            "VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE perm = VALUES(perm)";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, owner);
            stmt.setString(2, filename);
            stmt.setString(3, grantee);
            stmt.setString(4, perm.toUpperCase());
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean revokeFilePermission(String owner, String filename, String grantee) {
        String sql = "DELETE FROM file_permissions WHERE owner=? AND filename=? AND grantee=?";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, owner);
            stmt.setString(2, filename);
            stmt.setString(3, grantee);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public ObservableList<SharedFile> getSharedFilesFor(String grantee) {
        ObservableList<SharedFile> out = FXCollections.observableArrayList();

        String sql = "SELECT owner, filename, perm FROM file_permissions WHERE grantee=? ORDER BY owner, filename";
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, grantee);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new SharedFile(
                        rs.getString("owner"),
                        rs.getString("filename"),
                        rs.getString("perm")
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return out;
    }

    public String getPermission(String owner, String filename, String grantee) {
        String sql = "SELECT perm FROM file_permissions WHERE owner=? AND filename=? AND grantee=? LIMIT 1";

        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, owner);
            stmt.setString(2, filename);
            stmt.setString(3, grantee);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("perm");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    // permission to read or write
    public boolean hasPermission(String owner, String filename, String grantee, String neededPerm) {
        if (owner.equalsIgnoreCase(grantee)) return true; //owner always alloud 

        String perm = getPermission(owner, filename, grantee);
        if (perm == null) return false;

        perm = perm.toUpperCase();
        neededPerm = neededPerm.toUpperCase();

        if (neededPerm.equals("READ")) {
            return perm.equals("READ") || perm.equals("WRITE");
        }
        return perm.equals("WRITE");
    }
}