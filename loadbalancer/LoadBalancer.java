package loadbalancer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class LoadBalancer {

    private static final List<InetSocketAddress> BACKENDS = loadBackends();

    private static final int LISTEN_PORT = 9000;

    private static final int HC_INTERVAL_MS = 1000;
    private static final int CONNECT_TIMEOUT_MS = 800;
    private static final int IO_TIMEOUT_MS = 1200;

    private static final boolean[] healthy = new boolean[BACKENDS.size()];
    private static final Object healthLock = new Object();

    public static void main(String[] args) throws Exception {
        Arrays.fill(healthy, true);

        ScheduledExecutorService sch = Executors.newSingleThreadScheduledExecutor();
        sch.scheduleAtFixedRate(LoadBalancer::runHealthChecks, 0, HC_INTERVAL_MS, TimeUnit.MILLISECONDS);

        System.out.println("[LB] LoadBalancer started on port " + LISTEN_PORT);
        System.out.flush();

        try (ServerSocket server = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                Socket client = server.accept();
                new Thread(() -> handleClient(client)).start();
            }
        }
    }

    private static List<InetSocketAddress> loadBackends() {
        String raw = System.getenv().getOrDefault("LB_BACKENDS", "localhost:9101,localhost:9102");
        List<InetSocketAddress> out = new ArrayList<>();

        for (String item : raw.split(",")) {
            item = item.trim();
            if (item.isEmpty()) continue;

            String[] hp = item.split(":");
            if (hp.length != 2) continue;

            String host = hp[0].trim();
            int port = Integer.parseInt(hp[1].trim());

            out.add(new InetSocketAddress(host, port));
        }

        if (out.isEmpty()) {
            throw new IllegalStateException("No backends configured (LB_BACKENDS empty/invalid)");
        }

        return out;
    }

    private static void runHealthChecks() {
        for (int i = 0; i < BACKENDS.size(); i++) {
            InetSocketAddress addr = BACKENDS.get(i);
            boolean ok = ping(addr);
            synchronized (healthLock) {
                healthy[i] = ok;
            }
        }
    }

    private static boolean ping(InetSocketAddress addr) {
        try (Socket s = new Socket()) {
            s.connect(addr, CONNECT_TIMEOUT_MS);
            s.setSoTimeout(IO_TIMEOUT_MS);

            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            out.write("PING\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            s.shutdownOutput();

            String resp = readLine(in);
            return resp != null && resp.startsWith("OK");
        } catch (Exception e) {
            return false;
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

            int idx = pickBackendIndex(username);
            backend = connectHealthyBackend(idx);

            InetSocketAddress chosen = (InetSocketAddress) backend.getRemoteSocketAddress();
            System.out.println("[LB] " + client.getRemoteSocketAddress()
                    + " user=" + username
                    + " cmd=\"" + header + "\""
                    + " -> " + chosen);
            System.out.flush();

            backend.setSoTimeout(IO_TIMEOUT_MS);

            InputStream bin = backend.getInputStream();
            OutputStream bout = backend.getOutputStream();

            bout.write(headerBytes);
            bout.flush();

            Thread t1 = pipe(cin, bout);
            Thread t2 = pipe(bin, cout);

            t1.join();
            t2.join();

        } catch (Exception e) {
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

    private static Socket connectHealthyBackend(int preferredIdx) throws IOException {
        List<Integer> order = new ArrayList<>();
        order.add(preferredIdx);
        for (int i = 0; i < BACKENDS.size(); i++) if (i != preferredIdx) order.add(i);

        IOException last = null;

        for (int idx : order) {
            if (!isHealthy(idx)) continue;
            InetSocketAddress addr = BACKENDS.get(idx);
            try {
                Socket s = new Socket();
                s.connect(addr, CONNECT_TIMEOUT_MS);
                return s;
            } catch (IOException e) {
                last = e;
                setHealthy(idx, false);
            }
        }

        throw new IOException("No healthy backends", last);
    }

    private static boolean isHealthy(int idx) {
        synchronized (healthLock) {
            return healthy[idx];
        }
    }

    private static void setHealthy(int idx, boolean ok) {
        synchronized (healthLock) {
            healthy[idx] = ok;
        }
    }

    private static int pickBackendIndex(String username) {
        if (username == null || username.isBlank()) return 0;
        return Math.floorMod(username.hashCode(), BACKENDS.size());
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