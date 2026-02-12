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

    private static final String LB_HOST = "load-balancer";
    private static final int LB_PORT = 9000;
    
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final String MANIFEST_SUFFIX = ".manifest";
    private static final String PART_PREFIX = ".part";

    private static String manifestName(String filename) {
        return filename + MANIFEST_SUFFIX;
    }

    private static String partName(String filename, int idx) {
        return filename + PART_PREFIX + String.format("%05d", idx);
    }

    private static boolean isManifest(String name) {
        return name != null && name.endsWith(MANIFEST_SUFFIX);
    }

    private static boolean isPart(String name) {
        return name != null && name.matches(".*\\.part\\d{5}$");
    }

    private static Integer parsePartIndex(String name, String base) {
        String prefix = base + PART_PREFIX;
        if (!name.startsWith(prefix)) return null;
        String s = name.substring(prefix.length());
        if (s.length() != 5) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static int chunkCountForBytes(int totalBytes, int chunkSize) {
        return (totalBytes + chunkSize - 1) / chunkSize;
    }

    private static String buildManifest(int chunkCount) {
        return "COUNT=" + chunkCount + "\n";
    }

    private static int parseManifestCount(String manifestText) throws IOException {
        if (manifestText == null) throw new IOException("Bad manifest");
        String[] lines = manifestText.split("\\R");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("COUNT=")) {
                String v = line.substring("COUNT=".length()).trim();
                try { return Integer.parseInt(v); } catch (NumberFormatException ignored) {}
            }
        }
        throw new IOException("Bad manifest");
    }

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
    
    public static void setLoadBalancerAlgorithm(String username, String algorithm) throws IOException {
        try (Socket s = new Socket(LB_HOST, LB_PORT)) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            sendLine(out, "SETALG " + username + " " + algorithm);
            s.shutdownOutput();
            String resp = readLine(in);
            if (resp == null || !resp.startsWith("OK")) {
                throw new IOException("SETALG failed: " + resp);
            }
        }
    }
    
    public static String fetchLoadBalancerMetrics() throws IOException {
        try (Socket s = new Socket(LB_HOST, LB_PORT)) {
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
            //small enough: store as normal single file, and clean up any old chunked version
            if (encrypted.length <= CHUNK_SIZE) {
                LbReply r = lbWrite(username, filename, encrypted);
                if (!r.ok) throw new IOException("LB_WRITE failed: " + r.line);
            //cleanup chunked leftovers if they exist
                try {
                    LbReply m = lbRead(username, manifestName(filename));
                    if (m.ok && m.data != null) {
                        int count = parseManifestCount(new String(m.data, StandardCharsets.UTF_8));
                        for (int i = 0; i < count; i++) lbDelete(username, partName(filename, i));
                        lbDelete(username, manifestName(filename));
                    }
                } catch (Exception ignored) {}
                AppLogger.metric("FILE_WRITE user=" + username + " file=" + filename, msSince(t0));
                Metrics.end("file.write", t);
                return;
            }
            try {//cleanup previous chunked version if exists
                LbReply oldM = lbRead(username, manifestName(filename));
                if (oldM.ok && oldM.data != null) {
                    int oldCount = parseManifestCount(new String(oldM.data, StandardCharsets.UTF_8));
                    for (int i = 0; i < oldCount; i++) lbDelete(username, partName(filename, i));
                    lbDelete(username, manifestName(filename));
                }
            } catch (Exception ignored) {}
            //big files: write chunks and delete any old single file version
            int count = chunkCountForBytes(encrypted.length, CHUNK_SIZE);

            for (int i = 0; i < count; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, encrypted.length);
                byte[] chunk = java.util.Arrays.copyOfRange(encrypted, start, end);

                LbReply pr = lbWrite(username, partName(filename, i), chunk);
                if (!pr.ok) throw new IOException("LB_WRITE part failed: " + pr.line);
            }

            byte[] manifestBytes = buildManifest(count).getBytes(StandardCharsets.UTF_8);
            LbReply mr = lbWrite(username, manifestName(filename), manifestBytes);
            if (!mr.ok) throw new IOException("LB_WRITE manifest failed: " + mr.line);//remove old single file if it existed
            try { lbDelete(username, filename); } catch (Exception ignored) {}
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
            LbReply m = lbRead(username, manifestName(filename));
            if (m.ok && m.data != null) {
                int count = parseManifestCount(new String(m.data, StandardCharsets.UTF_8));
                for (int i = 0; i < count; i++) {
                    lbDelete(username, partName(filename, i));
                }
                lbDelete(username, manifestName(filename));
                // also attempt delete base file just in case
                try { lbDelete(username, filename); } catch (Exception ignored) {}

                AppLogger.metric("FILE_DELETE user=" + username + " file=" + filename, msSince(t0));
                Metrics.end("file.delete", t);
                return;
            }
            LbReply r = lbDelete(username, filename);
            if (!r.ok) throw new IOException("LB_DELETE failed: " + r.line);
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
            //Attempts to chunck first
            LbReply m = lbRead(username, manifestName(filename));
            if (m.ok && m.data != null) {
                int count = parseManifestCount(new String(m.data, StandardCharsets.UTF_8));
                ByteArrayOutputStream combined = new ByteArrayOutputStream();
                for (int i = 0; i < count; i++) {
                    LbReply pr = lbRead(username, partName(filename, i));
                    if (!pr.ok || pr.data == null) {
                        throw new IOException("Missing chunk: " + partName(filename, i));
                    }
                    combined.write(pr.data);
                }
                byte[] encryptedAll = combined.toByteArray();
                try {
                    byte[] plaintext = CryptUtil.decrypt(encryptedAll);
                    out = new String(plaintext, StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    AppLogger.warn("DECRYPT failed - treating as plaintext user=" + username + " file=" + filename);
                    out = new String(encryptedAll, StandardCharsets.UTF_8);
                }
                AppLogger.metric("FILE_READ user=" + username + " file=" + filename, msSince(t0));
                Metrics.end("file.read", t);
                return out;
            }
            //fallback for single file
            LbReply r = lbRead(username, filename);
            if (!r.ok || r.data == null) throw new IOException("LB_READ failed: " + r.line);
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
            return files.contains(filename) || files.contains(manifestName(filename));
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> listUserFiles(String username) throws IOException {
        long t = Metrics.start();
        long t0 = System.nanoTime();

        maybeDelay("LIST", username, "*");

        List<String> raw;
        synchronized (lockForUser(username)) {
            raw = lbList(username);
        }
        java.util.Set<String> manifests = new java.util.HashSet<>();
        java.util.Set<String> normals = new java.util.HashSet<>();

        for (String name : raw) {
            if (isManifest(name)) {
                String base = name.substring(0, name.length() - MANIFEST_SUFFIX.length());
                manifests.add(base);
            } else if (isPart(name)) {
                // ignore parts
            } else {
                normals.add(name);
            }
        }

        java.util.Set<String> out = new java.util.HashSet<>();
        out.addAll(normals);
        out.addAll(manifests);
        List<String> files = new ArrayList<>(out);
        files.sort(String::compareToIgnoreCase);
        AppLogger.metric("FILE_LIST user=" + username, msSince(t0));
        Metrics.end("file.list", t);
        return files;
    }

    public static Path userDir(String username) throws IOException {
        throw new UnsupportedOperationException("userDir is not used when storage is behind load balancer");
    }
}