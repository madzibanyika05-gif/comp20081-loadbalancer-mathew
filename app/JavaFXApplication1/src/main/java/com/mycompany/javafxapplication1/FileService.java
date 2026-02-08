/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.javafxapplication1;
/**
 *
 * @author ntu-user
 */
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class FileService {

    private static final String LB_HOST = "localhost";
    private static final int LB_PORT = 9000;

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
            Thread.sleep(delay);
            AppLogger.metric("ARTIFICIAL_DELAY op=" + op + " user=" + username + " file=" + filename, msSince(t0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AppLogger.warn("ARTIFICIAL_DELAY interrupted op=" + op + " user=" + username + " file=" + filename);
        }
    }

    private static void validateFilename(String filename) {
        if (filename == null) throw new SecurityException("Invalid filename");
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new SecurityException("Invalid filename");
        }
    }

    private static void sendLine(OutputStream out, String s) throws IOException {
        out.write((s + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            buf.write(b);
            if (buf.size() > 8192) return null;
        }
        if (buf.size() == 0 && b == -1) return null;
        return buf.toString(StandardCharsets.UTF_8).trim();
    }

    private static byte[] readFully(InputStream in, int byteCount) throws IOException {
        byte[] buf = new byte[byteCount];
        int off = 0;
        while (off < byteCount) {
            int n = in.read(buf, off, byteCount - off);
            if (n == -1) break;
            off += n;
        }
        if (off != byteCount) return null;
        return buf;
    }

    private static class LbReply {
        final boolean ok;
        final String line;
        final byte[] data;

        LbReply(boolean ok, String line, byte[] data) {
            this.ok = ok;
            this.line = line;
            this.data = data;
        }
    }

    private static LbReply lbWrite(String username, String filename, byte[] bytes) throws IOException {
        try (Socket s = new Socket(LB_HOST, LB_PORT)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            sendLine(out, "WRITE " + username + " " + filename + " " + bytes.length);
            out.write(bytes);
            out.flush();
            s.shutdownOutput();

            String resp = readLine(in);
            if (resp == null) return new LbReply(false, "ERR no_response", null);
            return new LbReply(resp.startsWith("OK"), resp, null);
        }
    }

    private static LbReply lbRead(String username, String filename) throws IOException {
        try (Socket s = new Socket(LB_HOST, LB_PORT)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            sendLine(out, "READ " + username + " " + filename);
            s.shutdownOutput();

            String hdr = readLine(in);
            if (hdr == null) return new LbReply(false, "ERR no_response", null);
            if (!hdr.startsWith("OK")) return new LbReply(false, hdr, null);

            String[] parts = hdr.split("\\s+");
            if (parts.length < 2) return new LbReply(false, "ERR bad_header", null);

            int n;
            try {
                n = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return new LbReply(false, "ERR bad_len", null);
            }

            byte[] data = readFully(in, n);
            if (data == null) return new LbReply(false, "ERR short_read", null);
            return new LbReply(true, hdr, data);
        }
    }

    private static LbReply lbDelete(String username, String filename) throws IOException {
        try (Socket s = new Socket(LB_HOST, LB_PORT)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            sendLine(out, "DELETE " + username + " " + filename);
            s.shutdownOutput();

            String resp = readLine(in);
            if (resp == null) return new LbReply(false, "ERR no_response", null);
            return new LbReply(resp.startsWith("OK"), resp, null);
        }
    }

    private static List<String> lbList(String username) throws IOException {
        try (Socket s = new Socket(LB_HOST, LB_PORT)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            sendLine(out, "LIST " + username);
            s.shutdownOutput();

            String hdr = readLine(in);
            if (hdr == null) throw new IOException("LIST failed: no_response");
            if (!hdr.startsWith("OK")) throw new IOException("LIST failed: " + hdr);

            String[] parts = hdr.split("\\s+");
            int count = 0;
            if (parts.length >= 2) {
                try { count = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
            }

            List<String> outNames = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = readLine(in);
                if (name == null) break;
                outNames.add(name);
            }
            Collections.sort(outNames);
            return outNames;
        }
    }
    
    public static String fetchLoadBalancerMetrics() throws IOException {
        try (Socket s = new Socket("localhost", 9000)) {
            s.setSoTimeout(2000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            out.write("METRICS\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            s.shutdownOutput();

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
            }
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    public static void writeTextFile(String username, String filename, String content) throws IOException {
        long t = Metrics.start();
        long t0 = System.nanoTime();

        maybeDelay("WRITE", username, filename);
        validateFilename(filename);

        synchronized (lockForUser(username)) {
            byte[] plaintext = content.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted;
            try {
                encrypted = CryptUtil.encrypt(plaintext);
            } catch (Exception ex) {
                AppLogger.error("ENCRYPT failed user=" + username + " file=" + filename, ex);
                throw new IOException("Encryption failed", ex);
            }

            LbReply r = lbWrite(username, filename, encrypted);
            if (!r.ok) {
                AppLogger.warn("LB_WRITE failed user=" + username + " file=" + filename + " resp=" + r.line);
                throw new IOException("LB_WRITE failed: " + r.line);
            }
        }

        AppLogger.metric("FILE_WRITE user=" + username + " file=" + filename, msSince(t0));
        Metrics.end("file.write", t);
    }

    public static void writeUserFile(String username, String filename, String content) throws IOException {
        writeTextFile(username, filename, content);
    }

    public static void deleteFile(String username, String filename) throws IOException {
        long t = Metrics.start();
        long t0 = System.nanoTime();

        maybeDelay("DELETE", username, filename);
        validateFilename(filename);

        synchronized (lockForUser(username)) {
            LbReply r = lbDelete(username, filename);
            if (!r.ok) {
                AppLogger.warn("LB_DELETE failed user=" + username + " file=" + filename + " resp=" + r.line);
                throw new IOException("LB_DELETE failed: " + r.line);
            }
        }

        AppLogger.metric("FILE_DELETE user=" + username + " file=" + filename, msSince(t0));
        Metrics.end("file.delete", t);
    }

    public static String readTextFile(String username, String filename) throws IOException {
        long t = Metrics.start();
        long t0 = System.nanoTime();

        maybeDelay("READ", username, filename);
        validateFilename(filename);

        String out;
        synchronized (lockForUser(username)) {
            LbReply r = lbRead(username, filename);
            if (!r.ok || r.data == null) {
                AppLogger.warn("LB_READ failed user=" + username + " file=" + filename + " resp=" + r.line);
                throw new IOException("LB_READ failed: " + r.line);
            }

            try {
                byte[] plaintext = CryptUtil.decrypt(r.data);
                out = new String(plaintext, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                AppLogger.warn("DECRYPT failed - treating as plaintext user=" + username + " file=" + filename);
                out = new String(r.data, StandardCharsets.UTF_8);
            }
        }

        AppLogger.metric("FILE_READ user=" + username + " file=" + filename, msSince(t0));
        Metrics.end("file.read", t);
        return out;
    }

    public static boolean fileExists(String username, String filename) {
        try {
            validateFilename(filename);
            List<String> files = lbList(username);
            return files.contains(filename);
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> listUserFiles(String username) throws IOException {
        long t = Metrics.start();
        long t0 = System.nanoTime();

        maybeDelay("LIST", username, "*");

        List<String> files;
        synchronized (lockForUser(username)) {
            files = lbList(username);
        }

        AppLogger.metric("FILE_LIST user=" + username, msSince(t0));
        Metrics.end("file.list", t);
        return files;
    }

    public static Path userDir(String username) throws IOException {
        throw new UnsupportedOperationException("userDir is not used when storage is behind load balancer");
    }
}