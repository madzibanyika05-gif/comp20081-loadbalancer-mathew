/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

/**
 *
 * @author ntu-user
 */
import java.util.stream.Collectors;
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
    
    public static void deleteFile(String username, String filename) throws IOException {
    Path file = userDir(username).resolve(filename);
    Files.deleteIfExists(file);
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
    
    public static java.util.List<String> listUserFiles(String username) throws IOException {
        java.nio.file.Path userDir = Configuration.STORAGE_LOCAL_DIR.resolve(username);

        if (!java.nio.file.Files.exists(userDir)) {
            return java.util.Collections.emptyList();
        }

        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(userDir)) {
            return stream
                .filter(java.nio.file.Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        }
    }
}
