package loadbalancer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

public class LoadBalancer {

    private static final int LISTEN_PORT = 9000;
    private static final int HC_INTERVAL_MS = 1000;
    private static final int CONNECT_TIMEOUT_MS = 800;
    private static final int IO_TIMEOUT_MS = 1200;

    private static final List<InetSocketAddress> BACKENDS = parseBackends();
    private static final boolean[] healthy = new boolean[BACKENDS.size()];
    private static final Object healthLock = new Object();

    private static final ConcurrentHashMap<String, LongAdder> cmdCounts = new ConcurrentHashMap<>();
    private static final LongAdder totalReq = new LongAdder();
    private static final LongAdder totalNanos = new LongAdder();
    private static final LongAdder[] backendCounts = new LongAdder[BACKENDS.size()];

    public static void main(String[] args) throws Exception {
        Arrays.fill(healthy, true);
        for (int i = 0; i < backendCounts.length; i++) backendCounts[i] = new LongAdder();

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

    private static List<InetSocketAddress> parseBackends() {
        String env = System.getenv("LB_BACKENDS");
        if (env == null || env.isBlank()) {
            return List.of(
                new InetSocketAddress("localhost", 9101),
                new InetSocketAddress("localhost", 9102)
            );
        }
        String[] parts = env.split(",");
        ArrayList<InetSocketAddress> out = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (s.isEmpty()) continue;
            String[] hp = s.split(":");
            if (hp.length != 2) continue;
            try {
                out.add(new InetSocketAddress(hp[0].trim(), Integer.parseInt(hp[1].trim())));
            } catch (Exception ignored) {}
        }
        if (out.isEmpty()) {
            out.add(new InetSocketAddress("localhost", 9101));
            out.add(new InetSocketAddress("localhost", 9102));
        }
        return Collections.unmodifiableList(out);
    }

    private static void runHealthChecks() {
        for (int i = 0; i < BACKENDS.size(); i++) {
            boolean ok = ping(BACKENDS.get(i));
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
        long start = System.nanoTime();
        try {
            InputStream cin = client.getInputStream();
            OutputStream cout = client.getOutputStream();
            byte[] headerBytes = readLineBytes(cin);
            if (headerBytes == null) return;

            String header = new String(headerBytes, StandardCharsets.UTF_8).trim();
            String[] parts = header.split("\\s+");
            String cmd = parts.length > 0 ? parts[0].toUpperCase(Locale.ROOT) : "";

            if ("METRICS".equals(cmd)) {
                cout.write(buildMetricsSnapshot().getBytes(StandardCharsets.UTF_8));
                cout.flush();
                return;
            }

            String username = extractUsername(header);
            int preferredIdx = pickBackendIndex(username);
            backend = connectHealthyBackend(preferredIdx);
            int chosenIdx = indexOfBackend((InetSocketAddress) backend.getRemoteSocketAddress());
            backend.setSoTimeout(IO_TIMEOUT_MS);

            InputStream bin = backend.getInputStream();
            OutputStream bout = backend.getOutputStream();
            bout.write(headerBytes);
            bout.flush();

            Thread t1 = pipe(cin, bout);
            Thread t2 = pipe(bin, cout);
            t1.join();
            t2.join();

            recordMetrics(cmd, chosenIdx, start);
        } catch (Exception e) {
            try {
                client.getOutputStream().write("ERR backend_unavailable\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        } finally {
            try { if (backend != null) backend.close(); } catch (IOException ignored) {}
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private static void recordMetrics(String cmd, int backendIdx, long startNano) {
        totalReq.increment();
        totalNanos.add(System.nanoTime() - startNano);
        if (cmd != null && !cmd.isBlank()) cmdCounts.computeIfAbsent(cmd, k -> new LongAdder()).increment();
        if (backendIdx >= 0 && backendIdx < backendCounts.length) backendCounts[backendIdx].increment();
    }

    private static int indexOfBackend(InetSocketAddress remote) {
        if (remote == null) return -1;
        for (int i = 0; i < BACKENDS.size(); i++) {
            InetSocketAddress b = BACKENDS.get(i);
            if (Objects.equals(b.getHostString(), remote.getHostString()) && b.getPort() == remote.getPort()) return i;
        }
        return -1;
    }

    private static String buildMetricsSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LOAD BALANCER METRICS ===\n");
        sb.append("backends=").append(BACKENDS.size()).append("\n");
        long total = totalReq.sum();
        sb.append("requests_total=").append(total).append("\n");
        double avgMs = total > 0 ? (totalNanos.sum() / 1_000_000.0) / total : 0.0;
        sb.append(String.format(Locale.ROOT, "avg_request_ms=%.2f\n", avgMs));
        sb.append("\n-- commands --\n");
        cmdCounts.keySet().stream().sorted().forEach(k ->
            sb.append(k).append("=").append(cmdCounts.get(k).sum()).append("\n")
        );
        sb.append("\n-- per_backend --\n");
        for (int i = 0; i < BACKENDS.size(); i++) {
            InetSocketAddress b = BACKENDS.get(i);
            sb.append(b.getHostString()).append(":").append(b.getPort())
              .append(" count=").append(backendCounts[i].sum())
              .append(" healthy=").append(isHealthy(i)).append("\n");
        }
        return sb.append("\n").toString();
    }

    private static Socket connectHealthyBackend(int preferredIdx) throws IOException {
        List<Integer> order = new ArrayList<>();
        order.add(preferredIdx);
        for (int i = 0; i < BACKENDS.size(); i++) if (i != preferredIdx) order.add(i);
        for (int idx : order) {
            if (!isHealthy(idx)) continue;
            try {
                Socket s = new Socket();
                s.connect(BACKENDS.get(idx), CONNECT_TIMEOUT_MS);
                return s;
            } catch (IOException e) {
                setHealthy(idx, false);
            }
        }
        throw new IOException("No healthy backends");
    }

    private static boolean isHealthy(int idx) {
        synchronized (healthLock) { return healthy[idx]; }
    }

    private static void setHealthy(int idx, boolean ok) {
        synchronized (healthLock) { healthy[idx] = ok; }
    }

    private static int pickBackendIndex(String username) {
        return (username == null || username.isBlank()) ? 0 : Math.floorMod(username.hashCode(), BACKENDS.size());
    }

    private static String extractUsername(String header) {
        if (header == null) return null;
        String[] parts = header.trim().split("\\s+");
        return parts.length >= 2 ? parts[1] : null;
    }

    private static byte[] readLineBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            if (b == '\n') break;
            if (buf.size() > 8192) throw new IOException("Header too long");
        }
        return buf.size() == 0 && b == -1 ? null : buf.toByteArray();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') buf.write(b);
        return buf.size() == 0 && b == -1 ? null : buf.toString(StandardCharsets.UTF_8).trim();
    }

    private static Thread pipe(InputStream in, OutputStream out) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            try {
                int n;
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
