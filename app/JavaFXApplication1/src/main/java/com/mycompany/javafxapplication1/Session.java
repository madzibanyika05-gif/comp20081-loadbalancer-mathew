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

    private Session() {
        // prevent instantiation
    }

    public static void login(String user) {
        username = user;
    }

    public static String getUsername() {
        return username;
    }

    public static boolean isLoggedIn() {
        return username != null && !username.isBlank();
    }

    public static void logout() {
        username = null;
    }
}
