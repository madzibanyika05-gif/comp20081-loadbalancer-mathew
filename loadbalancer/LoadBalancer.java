package loadbalancer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LoadBalancer {

    private static final List<InetSocketAddress> BACKENDS = List.of(
            new InetSocketAddress("localhost", 9101),
            new InetSocketAddress("localhost", 9102)
    );

    private static final int LISTEN_PORT = 9000;

    public static void main(String[] args) throws Exception {
        System.out.println("[LB] LoadBalancer started on port " + LISTEN_PORT);
        System.out.flush();

        try (ServerSocket server = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                Socket client = server.accept();
                new Thread(() -> handleClient(client)).start();
            }
        }
    }

    private static void handleClient(Socket client) {
        Socket backend = null;

        try {
            InputStream cin = client.getInputStream();
            OutputStream cout = client.getOutputStream();

            byte[] headerBytes = readLineBytes(cin);
            if (headerBytes == null) return;

            String header = new String(headerBytes, StandardCharsets.UTF_8).trim();
            String username = extractUsername(header);
            InetSocketAddress preferred = pickBackend(username);

            backend = connectWithFallback(preferred);

            InetSocketAddress chosen = (InetSocketAddress) backend.getRemoteSocketAddress();
            System.out.println("[LB] " + client.getRemoteSocketAddress()
                    + " user=" + username
                    + " cmd=\"" + header + "\""
                    + " -> " + chosen);
            System.out.flush();

            InputStream bin = backend.getInputStream();
            OutputStream bout = backend.getOutputStream();

            bout.write(headerBytes);
            bout.flush();

            Thread t1 = pipe(cin, bout);
            Thread t2 = pipe(bin, cout);

            t1.join();
            t2.join();

        } catch (Exception e) {
            System.out.println("[LB] ERROR: " + e.getMessage());
            System.out.flush();
            try {
                OutputStream cout = client.getOutputStream();
                cout.write(("ERR backend_unavailable\n").getBytes(StandardCharsets.UTF_8));
                cout.flush();
            } catch (IOException ignored) {}
        } finally {
            try { if (backend != null) backend.close(); } catch (IOException ignored) {}
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private static byte[] readLineBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            if (b == '\n') break;
            if (buf.size() > 8192) throw new IOException("Header too long");
        }
        if (buf.size() == 0 && b == -1) return null;
        return buf.toByteArray();
    }

    private static String extractUsername(String header) {
        if (header == null) return null;
        String[] parts = header.trim().split("\\s+");
        if (parts.length < 2) return null;

        String cmd = parts[0].toUpperCase();
        if (cmd.equals("LIST") || cmd.equals("READ") || cmd.equals("DELETE") || cmd.equals("WRITE")) {
            return parts[1];
        }
        return null;
    }

    private static InetSocketAddress pickBackend(String username) {
        if (username == null || username.isBlank()) return BACKENDS.get(0);
        int idx = Math.floorMod(username.hashCode(), BACKENDS.size());
        return BACKENDS.get(idx);
    }

    private static Socket connectWithFallback(InetSocketAddress preferred) throws IOException {
        IOException last = null;

        try {
            Socket s = new Socket();
            s.connect(preferred, 1000);
            return s;
        } catch (IOException e) {
            last = e;
        }

        for (InetSocketAddress addr : BACKENDS) {
            if (addr.equals(preferred)) continue;
            try {
                Socket s = new Socket();
                s.connect(addr, 1000);
                return s;
            } catch (IOException e) {
                last = e;
            }
        }

        throw new IOException("No backends available", last);
    }

    private static Thread pipe(InputStream in, OutputStream out) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            int n;
            try {
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {}
        });
        t.start();
        return t;
    }
}