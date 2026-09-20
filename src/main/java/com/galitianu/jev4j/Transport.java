package com.galitianu.jev4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;

/** HTTP transport: headers, JSON, per-attempt timeouts, and retries. */
final class Transport {

    /**
     * Method, path, status, timing and retry decisions. DEBUG, never higher: a library has no
     * business writing to an application's logs during normal operation, and INFO is on by
     * default in both java.util.logging and Spring Boot.
     */
    private static final System.Logger HTTP = System.getLogger("com.galitianu.jev4j.http");

    /**
     * Request and response bodies, at TRACE only. Separate from {@link #HTTP} because the body
     * carries whatever the caller passed as the state, which is usually their users' content;
     * turning on request tracing should not be a side effect of debugging latency.
     */
    private static final System.Logger WIRE = System.getLogger("com.galitianu.jev4j.wire");
    private static final Set<String> RESERVED_HEADERS = Set.of(
        "authorization", "accept", "content-type", "user-agent", "x-typesafe-sdk", "x-typesafe-runtime", "x-typesafe-retry-count");

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final RetryPolicy retry;
    private final Map<String, String> defaultHeaders;
    private final DoubleSupplier random;
    private final AtomicLong requestCount = new AtomicLong();

    Transport(HttpClient http, ObjectMapper mapper, String baseUrl, String apiKey, Duration timeout, RetryPolicy retry,
              Map<String, String> defaultHeaders, DoubleSupplier random) {
        this.http = http;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.retry = retry;
        this.defaultHeaders = defaultHeaders;
        this.random = random;
    }

    /** Sends a request and returns the parsed JSON body of a 2xx response. */
    CompletableFuture<JsonNode> send(String method, String path, Object body, RequestOptions options) {
        Duration attemptTimeout = options.timeout().orElse(timeout);
        RetryPolicy policy = options.retry().orElse(retry);
        String tag = "#" + requestCount.incrementAndGet() + " " + method + " " + path;

        String json;
        try {
            json = body == null ? null : mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(new TypeSafeException("Cannot serialize the request body to JSON: " + e.getOriginalMessage(), e));
        }

        Map<String, String> headers = new LinkedHashMap<>();
        defaultHeaders.forEach((k, v) -> putUnlessReserved(headers, k, v));
        options.headers().forEach((k, v) -> putUnlessReserved(headers, k, v));
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Accept", "application/json");
        headers.put("User-Agent", Version.USER_AGENT);
        headers.put("X-TypeSafe-SDK", Version.USER_AGENT);
        headers.put("X-TypeSafe-Runtime", "java/" + Runtime.version().feature());
        if (json != null) {
            headers.put("Content-Type", "application/json");
        }

        Prepared prepared = new Prepared(tag, method, URI.create(baseUrl + path), headers, json, attemptTimeout, policy);
        HTTP.log(System.Logger.Level.DEBUG, () -> tag + " -> " + prepared.uri);
        if (json != null) {
            WIRE.log(System.Logger.Level.TRACE, () -> tag + " request body: " + json);
        }
        return attemptWithRetries(prepared, 0).thenApply(res -> parseBody(res.body(), res.headers()));
    }

    private static void putUnlessReserved(Map<String, String> headers, String name, String value) {
        if (RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            // The only thing worth warning about: the caller asked for something that did not
            // happen, and nothing else would ever tell them why the header never arrived.
            HTTP.log(System.Logger.Level.WARNING,
                () -> "Ignoring header \"" + name + "\": the SDK sets it and it cannot be overridden.");
            return;
        }
        headers.put(name, value);
    }

    private record Prepared(String tag, String method, URI uri, Map<String, String> headers, String body, Duration timeout, RetryPolicy retry) {}

    private CompletableFuture<HttpResponse<String>> attemptWithRetries(Prepared p, int attempt) {
        int retriesLeft = p.retry.maxRetries() - attempt;
        long started = System.nanoTime();
        return attemptOnce(p, attempt).handle((res, err) -> {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            if (err != null) {
                TypeSafeException failure = classify(err, p.timeout);
                // Logged at DEBUG only: the failure is thrown to the caller, and logging it here
                // as well would report the same problem twice from two different places.
                HTTP.log(System.Logger.Level.DEBUG, () -> p.tag + " failed after " + elapsedMs + "ms: " + failure.getMessage());
                if (retriesLeft <= 0 || !p.retry.retries(failure)) {
                    return CompletableFuture.<HttpResponse<String>>failedFuture(failure);
                }
                return backOff(p, attempt, retriesLeft, failure.getMessage(), null);
            }
            String requestId = res.headers().firstValue(ApiException.REQUEST_ID_HEADER).orElse(null);
            HTTP.log(System.Logger.Level.DEBUG, () -> p.tag + " <- " + res.statusCode() + " in " + elapsedMs + "ms"
                + (requestId == null ? "" : " (request " + requestId + ")"));
            WIRE.log(System.Logger.Level.TRACE, () -> p.tag + " response body: " + res.body());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return CompletableFuture.completedFuture(res);
            }
            JsonNode errorBody = parseBody(res.body(), res.headers());
            ApiException failure = ApiException.fromResponse(res.statusCode(), errorBody, res.headers());
            if (retriesLeft <= 0 || !p.retry.retries(res.statusCode())) {
                return CompletableFuture.<HttpResponse<String>>failedFuture(failure);
            }
            return backOff(p, attempt, retriesLeft, String.valueOf(res.statusCode()), res.headers());
        }).thenCompose(f -> f);
    }

    private CompletableFuture<HttpResponse<String>> backOff(Prepared p, int attempt, int retriesLeft, String reason, HttpHeaders headers) {
        Duration delay = RetryDelays.delayFor(attempt, headers, p.retry, random);
        HTTP.log(System.Logger.Level.DEBUG, () -> p.tag + " retrying in " + delay.toMillis() + "ms (retry " + (attempt + 1) + "/"
            + (attempt + retriesLeft) + ") after " + reason);
        Executor delayed = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS);
        return CompletableFuture.supplyAsync(() -> null, delayed).thenCompose(v -> attemptWithRetries(p, attempt + 1));
    }

    private CompletableFuture<HttpResponse<String>> attemptOnce(Prepared p, int attempt) {
        HttpRequest.Builder b = HttpRequest.newBuilder(p.uri).timeout(p.timeout);
        p.headers.forEach(b::header);
        if (attempt > 0) {
            b.header("X-TypeSafe-Retry-Count", String.valueOf(attempt));
        }
        b.method(p.method, p.body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(p.body));
        try {
            return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
                .orTimeout(p.timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static TypeSafeException classify(Throwable err, Duration timeout) {
        Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
        if (cause instanceof TypeSafeException t) {
            return t;
        }
        if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) {
            return new ApiTimeoutException(timeout, cause);
        }
        if (cause instanceof IOException || cause instanceof SecurityException || cause instanceof IllegalArgumentException) {
            return new ApiConnectionException("Connection error: " + cause.getMessage(), cause);
        }
        return new ApiConnectionException("Connection error: " + cause, cause);
    }

    /** Parses a JSON body; non-JSON text becomes a text node; an empty body becomes {@code null}. */
    private JsonNode parseBody(String text, HttpHeaders headers) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String contentType = headers.firstValue("content-type").orElse("");
        if (contentType.contains("application/json")) {
            try {
                return mapper.readTree(text);
            } catch (JsonProcessingException e) {
                return TextNode.valueOf(text);
            }
        }
        return TextNode.valueOf(text);
    }
}
