/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import java.sql.*;

/**
 *
 * @author ntu-user
 */
public class LocalSQLiteDB {

    private static final String URL = "jdbc:sqlite:local.db";

    public static Connection getConn() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void ensureSchema() {
        try (Connection c = getConn(); Statement st = c.createStatement()) {

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS session (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "username TEXT NOT NULL, " +
                "role TEXT NOT NULL, " +
                "login_time TEXT NOT NULL" +
                ")"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS temp_file_meta (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL, " +
                "filename TEXT NOT NULL, " +
                "op TEXT NOT NULL, " +
                "bytes INTEGER NOT NULL, " +
                "ts TEXT NOT NULL" +
                ")"
            );

        } catch (SQLException e) {
            AppLogger.error("SQLITE ensureSchema failed", e);
        }
    }

    // ---- session helpers ----
    public static void saveSession(String username, String role) {
        try (Connection c = getConn()) {
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO session (id, username, role, login_time) " +
                "VALUES (1, ?, ?, datetime('now')) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "username=excluded.username, " +
                "role=excluded.role, " +
                "login_time=excluded.login_time"
            )) {
                ps.setString(1, username);
                ps.setString(2, role);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            AppLogger.error("SQLITE saveSession failed", e);
        }
    }

    public static void clearSession() {
        try (Connection c = getConn();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM session WHERE id=1");
        } catch (SQLException e) {
            AppLogger.error("SQLITE clearSession failed", e);
        }
    }

    public static SessionData loadSession() {
        try (Connection c = getConn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT username, role FROM session WHERE id=1")) {

            if (rs.next()) {
                return new SessionData(rs.getString("username"), rs.getString("role"));
            }
            return null;

        } catch (SQLException e) {
            AppLogger.error("SQLITE loadSession failed", e);
            return null;
        }
    }

    // ---- temp metadata helpers ----
    public static void logTempMeta(String username, String filename, String op, long bytes) {
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO temp_file_meta(username, filename, op, bytes, ts) " +
                "VALUES (?, ?, ?, ?, datetime('now'))"
            )) {
            ps.setString(1, username);
            ps.setString(2, filename);
            ps.setString(3, op);
            ps.setLong(4, bytes);
            ps.executeUpdate();
        } catch (SQLException e) {
            AppLogger.error("SQLITE logTempMeta failed", e);
        }
    }

    public static class SessionData {
        private final String username;
        private final String role;

        public SessionData(String username, String role) {
            this.username = username;
            this.role = role;
        }

        public String username() { return username; }
        public String role() { return role; }
    }
}