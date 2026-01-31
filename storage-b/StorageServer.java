import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class StorageServer {

    private final Path baseDir;

    public StorageServer(Path baseDir) {
        this.baseDir = baseDir;
    }

    private static void sendLine(BufferedWriter out, String s) throws IOException {
        out.write(s);
        out.write("\n");
        out.flush();
    }

    private static String safeName(String name) {//stop path traversal
        if (name == null) return null;
        name = name.trim();
        if (name.isEmpty()) return null;
        if (name.contains("..") || name.contains("/") || name.contains("\\")) return null;
        return name;
    }

    private void handle(Socket client) {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()))) {

            String line = in.readLine();
            if (line == null) return;

            String[] parts = line.split(" ");
            String cmd = parts[0].toUpperCase(Locale.ROOT);

            if (cmd.equals("PING")) {
                sendLine(out, "OK");
                return;
            }

            if (cmd.equals("LIST")) {
                if (parts.length < 2) { sendLine(out, "ERR bad_args"); return; }
                String user = safeName(parts[1]);
                if (user == null) { sendLine(out, "ERR bad_user"); return; }

                Path userDir = baseDir.resolve(user);
                Files.createDirectories(userDir);

                List<String> names = new ArrayList<>();
                try (var stream = Files.list(userDir)) {
                    stream.filter(Files::isRegularFile)
                          .map(p -> p.getFileName().toString())
                          .sorted()
                          .forEach(names::add);
                }

                sendLine(out, "OK " + names.size());
                for (String n : names) sendLine(out, n);
                return;
            }

            if (cmd.equals("READ")) {
                if (parts.length < 3) { sendLine(out, "ERR bad_args"); return; }
                String user = safeName(parts[1]);
                String file = safeName(parts[2]);
                if (user == null || file == null) { sendLine(out, "ERR bad_name"); return; }

                Path p = baseDir.resolve(user).resolve(file);
                if (!Files.exists(p)) { sendLine(out, "ERR not_found"); return; }

                byte[] data = Files.readAllBytes(p);
                sendLine(out, "OK " + data.length);
                client.getOutputStream().write(data);
                client.getOutputStream().flush();
                return;
            }

            if (cmd.equals("DELETE")) {
                if (parts.length < 3) { sendLine(out, "ERR bad_args"); return; }
                String user = safeName(parts[1]);
                String file = safeName(parts[2]);
                if (user == null || file == null) { sendLine(out, "ERR bad_name"); return; }

                Path p = baseDir.resolve(user).resolve(file);
                Files.deleteIfExists(p);
                sendLine(out, "OK");
                return;
            }

            if (cmd.equals("WRITE")) {
                if (parts.length < 4) { sendLine(out, "ERR bad_args"); return; }
                String user = safeName(parts[1]);
                String file = safeName(parts[2]);
                if (user == null || file == null) { sendLine(out, "ERR bad_name"); return; }

                int byteCount;
                try {
                    byteCount = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    sendLine(out, "ERR bad_len");
                    return;
                }
                if (byteCount < 0 || byteCount > 5_000_000) {
                    sendLine(out, "ERR len_range");
                    return;
                }

                Path userDir = baseDir.resolve(user);
                Files.createDirectories(userDir);
                Path p = userDir.resolve(file);

                byte[] data = client.getInputStream().readNBytes(byteCount);
                if (data.length != byteCount) { sendLine(out, "ERR short_read"); return; }

                Files.write(p, data);
                sendLine(out, "OK");
                return;
            }

            sendLine(out, "ERR unknown_cmd");

        } catch (Exception e) {
            //server crash prevention
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
        Path baseDir = Paths.get(".").toAbsolutePath().normalize();
        new StorageServer(baseDir).serve(port);
    }
}
