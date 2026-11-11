package com.uptime.monitor;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** Tiny local server with fixed endpoints for checker tests. */
public final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    public TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/ok", ex -> respond(ex, 200, "hello world"));
        server.createContext("/error", ex -> respond(ex, 500, "boom"));
        server.createContext("/redirect", ex -> {
            ex.getResponseHeaders().add("Location", "/ok");
            respond(ex, 302, "");
        });
        server.createContext("/to-metadata", ex -> {
            ex.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            respond(ex, 302, "");
        });
        server.createContext("/loop", ex -> {
            ex.getResponseHeaders().add("Location", "/loop");
            respond(ex, 302, "");
        });
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(ex, 200, "late");
        });
        server.start();
    }

    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
