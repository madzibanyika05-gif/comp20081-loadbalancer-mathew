/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/**
 *
 * @author ntu-user
 */
public class LocalSQLiteDB {

    // This creates a file called local.sqlite in your project folder (or wherever you point it)
    private static final String URL = "jdbc:sqlite:local.sqlite";

    public static void ensureSchema() {
        try (Connection c = DriverManager.getConnection(URL);
             Statement s = c.createStatement()) {

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS session (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    username TEXT NOT NULL,
                    role TEXT NOT NULL,
                    login_time_epoch INTEGER NOT NULL
                )
            """);

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS file_meta (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL,
                    filename TEXT NOT NULL,
                    last_action TEXT NOT NULL,
                    action_time_epoch INTEGER NOT NULL,
                    UNIQUE(username, filename)
                )
            """);

            AppLogger.info("SQLITE schema ensured");
        } catch (SQLException e) {
            AppLogger.error("SQLITE ensureSchema failed", e);
        }
    }