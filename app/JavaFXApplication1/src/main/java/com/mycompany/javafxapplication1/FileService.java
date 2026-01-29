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
import java.util.concurrent.ConcurrentHashMap;

public class FileService {

    private static final Path BASE_DIR = Configuration.STORAGE_LOCAL_DIR;
    
    private static final ConcurrentHashMap<String, Object> USER_LOCKS = new ConcurrentHashMap<>();

    private static Object lockForUser(String username) {
        
        return USER_LOCKS.computeIfAbsent(username, u -> new Object());
    }
    
    private static long msSince(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }
    
    private static void maybeDelay(String op, String username, String filename) {
        if (!Configuration.SIMULATE_DELAY) return;

        int min = Configuration.DELAY_MIN_MS;
        int max = Configuration.DELAY_MAX_MS;
        if (max < min) { int t = min; min = max; max = t; }

        int delay = min + (int)(Math.random() * (max - min + 1));

        long t0 = System.nanoTime();
        try {
            Thread.sleep(800);
            AppLogger.metric("ARTIFICIAL_DELAY op=" + op + " user=" + username + " file=" + filename, msSince(t0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AppLogger.warn("ARTIFICIAL_DELAY interrupted op=" + op + " user=" + username + " file=" + filename);
        }
    }

    public static Path userDir(String username) throws IOException {
        Path dir = BASE_DIR.resolve(username);
        Files.createDirectories(dir);
        return dir;
    }
    
    private static void validateFilename(String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new SecurityException("Invalid filename");
        }
    }
    
    public static void writeTextFile(String username, String filename, String content)
            throws IOException {
        
        long t = Metrics.start();
        long t0 = System.nanoTime();
        maybeDelay("WRITE", username, filename);
        synchronized (lockForUser(username)) {
            validateFilename(filename);
            Path userDir = userDir(username);
            Path file = userDir.resolve(filename);
            Files.writeString(file, content);
        }
        AppLogger.metric("FILE_WRITE user=" + username + " file=" + filename, msSince(t0));
        Metrics.end("file.write", t);
    }
    
    public static void deleteFile(String username, String filename) throws IOException {
        long t = Metrics.start();
        long t0 = System.nanoTime();
        maybeDelay("DELETE", username, filename);
        synchronized (lockForUser(username)) {
            validateFilename(filename);
            Path file = userDir(username).resolve(filename);
            Files.deleteIfExists(file);
        }
        AppLogger.metric("FILE_DELETE user=" + username + " file=" + filename, msSince(t0));
        Metrics.end("file.delete", t);
    }
    
    public static String readTextFile(String username, String filename)
            throws IOException {
        
        long t = Metrics.start();
        long t0 = System.nanoTime();
        String out;
        maybeDelay("READ", username, filename);
        synchronized (lockForUser(username)) {
            validateFilename(filename);
            Path file = userDir(username).resolve(filename);
            out = Files.readString(file);
        }
        AppLogger.metric("FILE_READ user=" + username + " file=" + filename, msSince(t0));
        Metrics.end("file.read", t);
        return out;
    }
    
    public static void writeUserFile(String username, String filename, String content)
        throws IOException {

    Path userDir = userDir(username); // make sures diactory exists
    Path file = userDir.resolve(filename);
    Files.writeString(file, content);
    }
    
    public static boolean fileExists(String username, String filename) {
        try {
            Path file = userDir(username).resolve(filename);
            return Files.exists(file);
        } catch (IOException e) {
            return false;
        }
    }
    
    public static java.util.List<String> listUserFiles(String username) throws IOException {
        long t = Metrics.start();
        long t0 = System.nanoTime();
        java.util.List<String> files;
        
        maybeDelay("LIST", username, "*");
        synchronized (lockForUser(username)) {
            java.nio.file.Path userDir = Configuration.STORAGE_LOCAL_DIR.resolve(username);

            if (!java.nio.file.Files.exists(userDir)) {
                files = java.util.Collections.emptyList();
            } else {
                try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(userDir)) {
                    files = stream
                        .filter(java.nio.file.Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .collect(Collectors.toList());
                }
            }
        }

        AppLogger.metric("FILE_LIST user=" + username, msSince(t0));
        Metrics.end("file.list", t);
        return files;
    }
}