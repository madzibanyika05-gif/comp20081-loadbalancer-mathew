/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
/**
 *
 * @author ntu-user
 */
public class AppLogger {
    private static final Path LOG_PATH = Paths.get("app.log");

    private static void writeLine(String level, String msg) {
        String line = LocalDateTime.now() + " [" + level + "] " + msg + System.lineSeparator();
        try {
            Files.writeString(
                LOG_PATH,
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            // last resort: don't crash app because logging failed
            System.err.println("LOGGING FAILED: " + e.getMessage());
        }
    }

    public static void info(String msg) {
        writeLine("INFO", msg);
    }
    
    public static void warn(String message) {
        System.out.println("[WARN] " + message);
    }

    public static void error(String msg, Exception e) {
        writeLine("ERROR", msg + " | " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    
    public static void metric(String name, long ms) {
        writeLine("METRIC", name + " took " + ms + "ms");
    }
    
    public static String readLogTail(int maxLines) throws IOException {//method to read last N line in log file to keep things message small and clean
        java.nio.file.Path p = java.nio.file.Paths.get("app.log");

        if (!java.nio.file.Files.exists(p)) {
            return "No log file found (app.log).";
        }
        java.util.List<String> lines = java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8);

        int from = Math.max(0, lines.size() - maxLines);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.size(); i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }
}
