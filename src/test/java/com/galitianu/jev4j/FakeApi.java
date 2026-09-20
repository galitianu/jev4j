package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/** In-process HTTP server that replays scripted responses and records requests. */
final class FakeApi implements AutoCloseable {

    record Recorded(String method, String path, Map<String, List<String>> headers, JsonNode body) {
        String header(String name) {
            List<String> v = headers.get(name);
            return v == null || v.isEmpty() ? null : v.get(0);
        }
    }

    record Scripted(int status, Map<String, String> headers, String body) {}

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final ConcurrentLinkedQueue<Scripted> responses = new ConcurrentLinkedQueue<>();
    final List<Recorded> requests = new CopyOnWriteArrayList<>();
    volatile long delayMs = 0;

    FakeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    FakeApi json(int status, Object body) {
        return json(status, body, Map.of());
    }

    FakeApi json(int status, Object body, Map<String, String> headers) {
        try {
            Map<String, String> h = new java.util.LinkedHashMap<>(headers);
            h.put("Content-Type", "application/json");
            responses.add(new Scripted(status, h, MAPPER.writeValueAsString(body)));
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    FakeApi text(int status, String body) {
        responses.add(new Scripted(status, Map.of("Content-Type", "text/plain"), body));
        return this;
    }

    FakeApi ok(Map<String, Object> answers) {
        return json(200, Map.of("model", "jev-1.13.0", "answers", answers, "usage", Map.of("input_tokens", 12, "output_tokens", 3)));
    }

    private void handle(HttpExchange ex) throws IOException {
        byte[] raw = ex.getRequestBody().readAllBytes();
        JsonNode body = raw.length == 0 ? null : MAPPER.readTree(raw);
        requests.add(new Recorded(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestHeaders(), body));
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Scripted s = responses.poll();
        if (s == null) {
            s = new Scripted(500, Map.of("Content-Type", "text/plain"), "no scripted response");
        }
        s.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        byte[] out = s.body().getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(s.status(), out.length == 0 ? -1 : out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    static final class UncheckedIOException extends RuntimeException {
        UncheckedIOException(IOException e) {
            super(e);
        }
    }
}
