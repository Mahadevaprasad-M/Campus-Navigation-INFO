import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class NavigationServer {

    // Default port range. If 8080 is busy we try 8081, 8082, ... up to 8090.
    private static final int START_PORT = 8080;
    private static final int END_PORT   = 8090;

    // ---------------------------------------------------------------
    // Main — program starts here.
    // ---------------------------------------------------------------
    public static void main(String[] args) {

        printHeader();

        // Step 1: create the empty graph.
        CampusGraph graph = new CampusGraph();

        // Step 2: find where the JSON files live.
        String dataFolder = findDataFolder();
        if (dataFolder == null) {
            System.out.println();
            System.out.println("ERROR: Could not find locations.json anywhere.");
            System.out.println("Make sure backend/data/locations.json exists.");
            System.out.println("Common locations checked:");
            System.out.println("   backend/data/");
            System.out.println("   data/");
            System.out.println("   ../backend/data/");
            return;
        }

        // Step 3: load the JSON files into the graph.
        try {
            new LocationLoader(graph, dataFolder).loadAll();
        } catch (IOException e) {
            System.out.println();
            System.out.println("ERROR: Could not load campus data.");
            System.out.println("Reason: " + e.getMessage());
            return;
        }

        // Step 4: find the frontend folder (optional on backend-only deploy).
        String frontendFolder = findFrontendFolder();
        if (frontendFolder == null) {
            System.out.println();
            System.out.println("[!] frontend/ not found — static file serving disabled.");
            System.out.println("    This is normal when deploying the backend only.");
            System.out.println("    API endpoints will still work.");
        }

        // Step 5: pick a free port.
        int port = pickFreePort(START_PORT, END_PORT);
        if (port < 0) {
            System.out.println();
            System.out.println("ERROR: No free port in range " + START_PORT + "-" + END_PORT + ".");
            System.out.println("Close any program using those ports and try again.");
            return;
        }

        // Step 6: start the HTTP server.
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // API handlers (always on).
            server.createContext("/api/", new ApiHandler(graph));
            server.createContext("/api",  new ApiHandler(graph));

            // Static files if the frontend exists; otherwise a small info page.
            if (frontendFolder != null) {
                server.createContext("/", new StaticFileHandler(frontendFolder));
            } else {
                server.createContext("/", NavigationServer::handleRootInfo);
            }

            // 4 threads is plenty for a class demo.
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
        } catch (IOException e) {
            System.out.println();
            System.out.println("ERROR: Could not start server on port " + port);
            System.out.println("Reason: " + e.getMessage());
            return;
        }

        // Step 7: print the startup banner.
        printBanner(port, graph, frontendFolder, dataFolder);

        // Step 8: stop the server cleanly on Ctrl+C.
        final HttpServer runningServer = server;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("[*] Shutting down server...");
            runningServer.stop(1);
            System.out.println("[*] Server stopped. Goodbye.");
        }));

        // Step 9: keep the program running until Ctrl+C.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // Ignore — the shutdown hook handles everything.
        }
    }

    // ---------------------------------------------------------------
    // Fallback "/" handler when there is no frontend folder.
    // Just tells the caller that this is the API server.
    // ---------------------------------------------------------------
    private static void handleRootInfo(HttpExchange ex) throws IOException {
        String body = "Campus Navigation API is running.\n"
                + "Try /api/health or /api/path?from=entrance&to=principal\n";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        OutputStream out = ex.getResponseBody();
        out.write(bytes);
        out.close();
    }

    // ---------------------------------------------------------------
    // Try several common locations for backend/data/.
    // Return the first one that contains locations.json.
    // ---------------------------------------------------------------
    private static String findDataFolder() {

        String[] candidates = {
                "data",              // run from backend/
                "backend/data",      // run from project root
                "../backend/data",   // run from a subfolder
                "src/backend/data"
        };

        for (String folder : candidates) {
            Path testFile = Paths.get(folder, "locations.json");
            if (Files.exists(testFile)) return folder;
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Same idea, but looking for frontend/index.html.
    // ---------------------------------------------------------------
    private static String findFrontendFolder() {

        String[] candidates = {
                "frontend",           // run from project root
                "../frontend",        // run from backend/
                "backend/frontend",
                "src/frontend"
        };

        for (String folder : candidates) {
            Path testFile = Paths.get(folder, "index.html");
            if (Files.exists(testFile)) return folder;
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Try START_PORT, START_PORT+1, ... up to END_PORT.
    // Return -1 if none is free.
    // ---------------------------------------------------------------
    private static int pickFreePort(int startPort, int endPort) {
        for (int port = startPort; port <= endPort; port++) {
            if (isPortFree(port)) return port;
        }
        return -1;
    }

    // ---------------------------------------------------------------
    // A port is "free" if we can open a ServerSocket on it.
    // ---------------------------------------------------------------
    private static boolean isPortFree(int port) {
        ServerSocket testSocket = null;
        try {
            testSocket = new ServerSocket(port);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (testSocket != null) {
                try { testSocket.close(); } catch (IOException ignored) { }
            }
        }
    }

    // ---------------------------------------------------------------
    // Startup header.
    // ---------------------------------------------------------------
    private static void printHeader() {
        System.out.println();
        System.out.println("=================================================");
        System.out.println("  CAMPUS NAVIGATION SYSTEM");
        System.out.println("  Government Engineering College, Chamarajanagar");
        System.out.println("  3rd Semester CSE  |  Mini-Project");
        System.out.println("=================================================");
        System.out.println();
    }

    // ---------------------------------------------------------------
    // Startup banner with URLs and stats.
    // ---------------------------------------------------------------
    private static void printBanner(int port, CampusGraph graph,
                                    String frontendFolder, String dataFolder) {

        String baseUrl = "http://localhost:" + port;

        System.out.println();
        System.out.println("-------------------------------------------------");
        System.out.println("  SERVER RUNNING");
        System.out.println("-------------------------------------------------");
        System.out.println("  Port         : " + port);
        System.out.println("  Nodes        : " + graph.getNodeCount());
        System.out.println("  Edges        : " + graph.getEdgeCount());
        System.out.println("  Data folder  : " + dataFolder);
        System.out.println("  Frontend     : " + frontendFolder);
        System.out.println();
        System.out.println("  OPEN IN BROWSER:");
        System.out.println("    Home              " + baseUrl + "/");
        System.out.println("    QR demo           " + baseUrl + "/loc/entrance");
        System.out.println();
        System.out.println("  API ENDPOINTS:");
        System.out.println("    All locations     " + baseUrl + "/api/locations");
        System.out.println("    One location      " + baseUrl + "/api/location/library");
        System.out.println("    Route             " + baseUrl + "/api/path?from=entrance&to=principal");
        System.out.println("    Route (BFS)       " + baseUrl + "/api/path?from=entrance&to=principal&algo=bfs");
        System.out.println("    Health check      " + baseUrl + "/api/health");
        System.out.println();
        System.out.println("  Press Ctrl+C to stop the server.");
        System.out.println("-------------------------------------------------");
        System.out.println();
    }
}