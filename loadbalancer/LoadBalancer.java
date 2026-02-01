package loadbalancer;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author ntu-user
 */
public class LoadBalancer {

    // backend storage nodes
    private static final List<InetSocketAddress> BACKENDS = List.of(
            new InetSocketAddress("localhost", 9101),
            new InetSocketAddress("localhost", 9102)
    );

    private static final int LISTEN_PORT = 9000;
    private static final AtomicInteger rr = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        System.out.println("[LB] LoadBalancer started on port " + LISTEN_PORT);

        try (ServerSocket server = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                Socket client = server.accept();
                InetSocketAddress backend = nextBackend();
                System.out.println("[LB] " + client.getRemoteSocketAddress()
                        + " -> " + backend);

                new Thread(() -> forward(client, backend)).start();
            }
        }
    }

    private static InetSocketAddress nextBackend() {
        int i = Math.floorMod(rr.getAndIncrement(), BACKENDS.size());
        return BACKENDS.get(i);
    }

    private static void forward(Socket client, InetSocketAddress backendAddr) {
        try (Socket backend = new Socket()) {
            backend.connect(backendAddr);

            Thread t1 = pipe(client.getInputStream(), backend.getOutputStream());
            Thread t2 = pipe(backend.getInputStream(), client.getOutputStream());

            t1.join();
            t2.join();
        } catch (Exception e) {
            System.out.println("[LB] ERROR: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
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
