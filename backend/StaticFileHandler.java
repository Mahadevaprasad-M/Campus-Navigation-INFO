import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler implements HttpHandler {

    // Folder where the frontend files live (index.html, style.css, ...).
    private Path rootFolder;

    public StaticFileHandler(String folderName) {

        if (folderName == null || folderName.length() == 0) {
            folderName = "frontend";
        }

        // Absolute + normalised, so path-traversal checks work correctly.
        this.rootFolder = Paths.get(folderName).toAbsolutePath().normalize();

        if (Files.isDirectory(this.rootFolder)) {
            System.out.println("[StaticFileHandler] Serving from: " + this.rootFolder);
        } else {
            System.out.println("[StaticFileHandler] WARNING: folder not found: " + this.rootFolder);
            System.out.println("[StaticFileHandler] Run the server from the project "
                    + "root, or from inside backend/, so it can find frontend/.");
        }
    }

    // ---------------------------------------------------------------
    // Main handler for every non-/api request.
    // ---------------------------------------------------------------
    public void handle(HttpExchange exchange) throws IOException {

        try {
            String method = exchange.getRequestMethod();

            // Only GET and HEAD.
            if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            String urlPath = exchange.getRequestURI().getPath();
            if (urlPath == null || urlPath.length() == 0) {
                urlPath = "/";
            }
            urlPath = urlDecode(urlPath);

            // "/" and "/index.html" -> serve index.html.
            if (urlPath.equals("/") || urlPath.equals("/index.html")) {
                serveIndex(exchange);
                return;
            }

            // "/loc" or "/loc/<id>" (QR codes) -> also serve index.html.
            // app.js reads the URL itself to know which place to show.
            if (urlPath.equals("/loc") || urlPath.startsWith("/loc/")) {
                serveIndex(exchange);
                return;
            }

            // Normal file request. Strip the leading "/".
            String relativePath = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;

            // Build and normalise the requested path.
            Path requestedFile = rootFolder.resolve(relativePath).normalize();

            // Security: the file must be inside rootFolder.
            // Blocks requests like "../../etc/passwd".
            if (!requestedFile.startsWith(rootFolder)) {
                System.out.println("[StaticFileHandler] Blocked traversal: " + urlPath);
                sendText(exchange, 403, "Forbidden");
                return;
            }

            // Missing file or a directory -> 404 page.
            if (!Files.exists(requestedFile) || Files.isDirectory(requestedFile)) {
                sendNotFound(exchange, urlPath);
                return;
            }

            byte[] fileBytes = Files.readAllBytes(requestedFile);
            String fileName    = requestedFile.getFileName().toString();
            String contentType = detectContentType(fileName);

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=60");

            // HEAD: send only headers.
            if (method.equalsIgnoreCase("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream out = exchange.getResponseBody();
            out.write(fileBytes);
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
            sendText(exchange, 500, "Internal Server Error");
        }
    }

    // ---------------------------------------------------------------
    // Serve index.html (used for "/" and every "/loc/..." route).
    // ---------------------------------------------------------------
    private void serveIndex(HttpExchange exchange) throws IOException {

        Path indexPath = rootFolder.resolve("index.html");

        if (!Files.exists(indexPath)) {
            sendText(exchange, 404,
                    "index.html not found in " + rootFolder
                            + "\nStart the server from the project root.");
            return;
        }

        byte[] bytes = Files.readAllBytes(indexPath);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);

        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    // ---------------------------------------------------------------
    // Friendly 404 page.
    // ---------------------------------------------------------------
    private void sendNotFound(HttpExchange exchange, String urlPath) throws IOException {

        String html = "<!DOCTYPE html>"
                + "<html><head><meta charset='utf-8'>"
                + "<title>404 — Not Found</title>"
                + "<style>"
                + "body { font-family: system-ui, Arial, sans-serif; "
                + "       max-width: 600px; margin: 60px auto; padding: 0 20px; "
                + "       color: #1e293b; }"
                + "h1 { color: #1d4ed8; }"
                + "code { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; }"
                + "a { color: #1d4ed8; }"
                + "</style></head><body>"
                + "<h1>404 — File Not Found</h1>"
                + "<p>The file <code>" + escapeHtml(urlPath) + "</code> "
                + "does not exist on this server.</p>"
                + "<p><a href='/'>Go to Campus Navigation home</a></p>"
                + "</body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(404, bytes.length);

        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    // ---------------------------------------------------------------
    // Send plain text with a status code.
    // ---------------------------------------------------------------
    private void sendText(HttpExchange exchange, int statusCode, String message)
            throws IOException {

        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    // ---------------------------------------------------------------
    // Pick a Content-Type from the file extension.
    // ---------------------------------------------------------------
    private String detectContentType(String fileName) {

        String lower = fileName.toLowerCase();

        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css"))                            return "text/css; charset=utf-8";
        if (lower.endsWith(".js"))                             return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json"))                           return "application/json; charset=utf-8";
        if (lower.endsWith(".svg"))                            return "image/svg+xml";
        if (lower.endsWith(".png"))                            return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif"))                            return "image/gif";
        if (lower.endsWith(".ico"))                            return "image/x-icon";
        if (lower.endsWith(".webp"))                           return "image/webp";
        if (lower.endsWith(".txt"))                            return "text/plain; charset=utf-8";
        if (lower.endsWith(".woff"))                           return "font/woff";
        if (lower.endsWith(".woff2"))                          return "font/woff2";

        // Unknown type: let the browser decide.
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------
    // URL-decode "%20" etc.
    // ---------------------------------------------------------------
    private String urlDecode(String text) {
        try {
            return URLDecoder.decode(text, "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }

    // ---------------------------------------------------------------
    // Escape text for safe HTML output.
    // ---------------------------------------------------------------
    private String escapeHtml(String text) {
        if (text == null) return "";

        String result = text;
        result = result.replace("&", "&amp;");
        result = result.replace("<", "&lt;");
        result = result.replace(">", "&gt;");
        result = result.replace("\"", "&quot;");
        return result;
    }
}