package h2go;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** The web server (Java's built-in HTTP server): /api/... goes to Api.java, anything else is a file from web/. */
public class WebServer implements HttpHandler {

    private HttpServer server;
    private Api api;

    public WebServer(H2Go app, String host, int port) throws IOException {
        api = new Api(app);
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
    }

    public void start() {
        server.createContext("/", this);
        server.start();
    }

    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.startsWith("/api/")) {
                api.handle(exchange, path);
            } else {
                sendFile(exchange, path);
            }
        } catch (Exception e) {
            System.out.println("Error in " + path + ": " + e);
            try {
                send(exchange, 500, "application/json", Json.error("Something went wrong on the server: " + e.getMessage()));
            } catch (IOException ignored) {
                // the browser has gone away
            }
        } finally {
            exchange.close();
        }
    }

    /** A file from web/, for example web/index.html for "/". */
    private void sendFile(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/")) {
            path = "/index.html";
        }
        Path file = Paths.get("web" + path);
        if (path.contains("..") || !Files.isRegularFile(file)) {
            send(exchange, 404, "text/plain", "Not found: " + path);
            return;
        }
        sendBytes(exchange, 200, contentType(path), Files.readAllBytes(file));
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (path.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    /** Sends text with a status code (200 OK, 400 bad input, 401 sign in first, ...). */
    public static void send(HttpExchange exchange, int status, String contentType, String text) throws IOException {
        sendBytes(exchange, status, contentType + "; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
