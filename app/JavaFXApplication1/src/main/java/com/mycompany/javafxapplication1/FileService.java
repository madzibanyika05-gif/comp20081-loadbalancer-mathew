/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

/**
 *
 * @author ntu-user
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileService {

    private static final Path BASE_DIR = Configuration.STORAGE_LOCAL_DIR;

    public static Path userDir(String username) throws IOException {
        Path dir = BASE_DIR.resolve(username);
        Files.createDirectories(dir);
        return dir;
    }

    public static void writeTextFile(String username, String filename, String content)
            throws IOException {

        Path userDir = userDir(username);
        Path file = userDir.resolve(filename);
        Files.writeString(file, content);
    }

    public static String readTextFile(String username, String filename)
            throws IOException {

        Path file = userDir(username).resolve(filename);
        return Files.readString(file);
    }
    
    public static void writeUserFile(String username, String filename, String content)
        throws IOException {

    Path userDir = userDir(username); // make sures diactory exists
    Path file = userDir.resolve(filename);
    Files.writeString(file, content);
    }
}
