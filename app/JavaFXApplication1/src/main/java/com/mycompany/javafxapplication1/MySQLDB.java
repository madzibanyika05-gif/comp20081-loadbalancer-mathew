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
}