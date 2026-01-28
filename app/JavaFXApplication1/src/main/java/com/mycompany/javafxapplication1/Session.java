/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

/**
 *
 * @author ntu-user
 */

public final class Session {

    private static String username;
    private static String role;

    private Session() {
        // prevent instantiation
    }

    public static void login(String user, String userRole) {
        username = user;
        role = userRole;
        LocalSQLiteDB.saveSession(username, role);
    }

    public static String getUsername() {
        return username;
    }

    public static String getRole() {
        return role;
    }
    
    public static boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
    
    public static boolean isLoggedIn() {
        return username != null && !username.isBlank();
    }

    public static void logout() {
        username = null;
        role = null;
        LocalSQLiteDB.clearSession();
    }
}
