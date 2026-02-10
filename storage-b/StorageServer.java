import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class StorageServer {

    private final Path baseDir;

    public StorageServer(Path baseDir) {
        this.baseDir = baseDir;
    }

    private static void sendLine(OutputStream out, String s) throws IOException {
        out.write((s + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String safeName(String name) {
        if (name == null) return null;
        name = name.trim();
        if (name.isEmpty()) return null;
        if (name.contains("..") || name.contains("/") || name.contains("\\")) return null;
        return name;
    }

    private static String readLineRaw(InputStream in) throws IOException {
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            headerBuf.write(b);
            if (headerBuf.size() > 8192) return null;
        }
        if (headerBuf.size() == 0 && b == -1) return null;
        return headerBuf.toString(StandardCharsets.UTF_8).trim();
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

    private void handle(Socket client) {
        try (client) {
            InputStream inRaw = client.getInputStream();
            OutputStream outRaw = client.getOutputStream();

            String line = readLineRaw(inRaw);
            if (line == null || line.isEmpty()) return;

            String[] parts = line.split(" ");
            String cmd = parts[0].toUpperCase(Locale.ROOT);

            if (cmd.equals("PING")) {
                sendLine(outRaw, "OK");
                return;
            }

            if (cmd.equals("LIST")) {
                if (parts.length < 2) { sendLine(outRaw, "ERR bad_args"); return; }
                String user = safeName(parts[1]);
                if (user == null) { sendLine(outRaw, "ERR bad_user"); return; }

                Path userDir = baseDir.resolve(user);
                Files.createDirectories(userDir);

                List<String> names = new ArrayList<>();
                try (var stream = Files.list(userDir)) {
                    stream.filter(Files::isRegularFile)
                          .map(p -> p.getFileName().toString())
                          .sorted()
                          .forEach(names::add);
                }

                sendLine(outRaw, "OK " + names.size());
                for (String n : names) sendLine(outRaw, n);
                return;
            }

            if (cmd.equals("READ")) {
                if (parts.length < 3) { sendLine(outRaw, "ERR bad_args"); return; }
                String user = safeName(parts[1]);
                String file = safeName(parts[2]);
                if (user == null || file == null) { sendLine(outRaw, "ERR bad_name"); return; }

                Path p = baseDir.resolve(user).resolve(file);
                if (!Files.exists(p)) { sendLine(outRaw, "ERR not_found"); return; }

                byte[] data = Files.readAllBytes(p);
                sendLine(outRaw, "OK " + data.length);
                outRaw.write(data);
                outRaw.flush();
                return;
            }

            if (cmd.equals("DELETE")) {
                if (parts.length < 3) { sendLine(outRaw, "ERR bad_args"); return; }
                String user = safeName(parts[1]);
                String file = safeName(parts[2]);
                if (user == null || file == null) { sendLine(outRaw, "ERR bad_name"); return; }

                Path p = baseDir.resolve(user).resolve(file);
                Files.deleteIfExists(p);
                sendLine(outRaw, "OK");
                return;
            }

            if (cmd.equals("WRITE")) {
                if (parts.length < 4) { sendLine(outRaw, "ERR bad_args"); return; }
                String user = safeName(parts[1]);
                String file = safeName(parts[2]);
                if (user == null || file == null) { sendLine(outRaw, "ERR bad_name"); return; }

                int byteCount;
                try {
                    byteCount = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    sendLine(outRaw, "ERR bad_len");
                    return;
                }
                if (byteCount < 0 || byteCount > 5_000_000) {
                    sendLine(outRaw, "ERR len_range");
                    return;
                }

                Path userDir = baseDir.resolve(user);
                Files.createDirectories(userDir);
                Path p = userDir.resolve(file);

                byte[] data = readFully(inRaw, byteCount);
                if (data == null) { sendLine(outRaw, "ERR short_read"); return; }

                Files.write(p, data);
                sendLine(outRaw, "OK");
                return;
            }

            sendLine(outRaw, "ERR unknown_cmd");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void serve(int port) throws IOException {
        Files.createDirectories(baseDir);
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("[STORAGE] Serving " + baseDir + " on port " + port);
            while (true) {
                Socket client = server.accept();
                new Thread(() -> handle(client)).start();
            }
        }
    }

    public static void main(String[] args) throws Exception {
    	int port = (args.length >= 1) ? Integer.parseInt(args[0]) : 9101;
    	Path baseDir;
    	if (args.length >= 2) {
            baseDir = Paths.get(args[1]).toAbsolutePath().normalize();
    	} else {
        baseDir = Paths.get(".").toAbsolutePath().normalize();
    	}
    	new StorageServer(baseDir).serve(port);
    }
}