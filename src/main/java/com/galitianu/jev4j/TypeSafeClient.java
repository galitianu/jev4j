package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleSupplier;

/**
 * Client for the TypeSafe AI API.
 *
 * <pre>{@code
 * try (TypeSafeClient client = TypeSafeClient.create()) {           // reads TYPESAFE_API_KEY
 *     var team = Choice.of("Which team owns this?", Team.class);
 *     var urgent = Noul.of("Is this urgent?");
 *     SystemOneResponse res = client.systemOne(ticketText, team, urgent);
 *     Team t = res.get(team).choice();
 *     double p = res.get(urgent).noul();
 * }
 * }</pre>
 *
 * <p>Configuration precedence: explicit builder values, then environment variables
 * ({@code TYPESAFE_API_KEY}, {@code TYPESAFE_BASE_URL}, {@code TYPESAFE_DEFAULT_MODEL}), then SDK defaults.
 * Instances are thread-safe.
 */
public final class TypeSafeClient implements AutoCloseable {

    public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";
    public static final String DEFAULT_MODEL = "jev-latest";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    public static final String ENV_API_KEY = "TYPESAFE_API_KEY";
    public static final String ENV_BASE_URL = "TYPESAFE_BASE_URL";
    public static final String ENV_DEFAULT_MODEL = "TYPESAFE_DEFAULT_MODEL";

    static final String SYSTEM_ONE_PATH = "/v1/systemone";

    private final Transport transport;
    private final AnswerDecoder decoder;
    private final Models models;
    private final String defaultModel;
    private final HttpClient ownedHttpClient;

    private TypeSafeClient(Builder b) {
        String apiKey = firstNonBlank(b.apiKey, env(ENV_API_KEY));
        if (apiKey == null) {
            throw new TypeSafeException("No API key. Pass apiKey(...) to the builder or set " + ENV_API_KEY + ".");
        }
        String baseUrl = stripTrailingSlash(firstNonBlank(b.baseUrl, env(ENV_BASE_URL), DEFAULT_BASE_URL));
        this.defaultModel = firstNonBlank(b.defaultModel, env(ENV_DEFAULT_MODEL), DEFAULT_MODEL);
        ObjectMapper mapper = b.objectMapper == null ? new ObjectMapper() : b.objectMapper;
        HttpClient http;
        if (b.httpClient == null) {
            http = HttpClient.newBuilder().connectTimeout(b.timeout).build();
            this.ownedHttpClient = http;
        } else {
            http = b.httpClient;
            this.ownedHttpClient = null;
        }
        this.transport = new Transport(http, mapper, baseUrl, apiKey, b.timeout, b.retry,
            Collections.unmodifiableMap(new LinkedHashMap<>(b.defaultHeaders)), b.random);
        this.decoder = new AnswerDecoder(mapper);
        this.models = new Models(transport);
    }

    /** A client configured entirely from environment variables and defaults. */
    public static TypeSafeClient create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The model used when a request does not override it. */
    public String defaultModel() {
        return defaultModel;
    }

    /** The Models resource. */
    public Models models() {
        return models;
    }

    /** Answers questions about a state in one call. Keys are positional unless a handle is {@code named}. */
    public SystemOneResponse systemOne(Object state, Question<?>... questions) {
        return systemOne(SystemOneRequest.builder().state(state).questions(questions).build());
    }

    /** Answers string-keyed questions about a state in one call. */
    public SystemOneResponse systemOne(Object state, Map<String, ? extends Question<?>> questions) {
        return systemOne(SystemOneRequest.builder().state(state).questions(questions).build());
    }

    public SystemOneResponse systemOne(SystemOneRequest request) {
        return Futures.join(systemOneAsync(request));
    }

    public CompletableFuture<SystemOneResponse> systemOneAsync(Object state, Question<?>... questions) {
        return systemOneAsync(SystemOneRequest.builder().state(state).questions(questions).build());
    }

    public CompletableFuture<SystemOneResponse> systemOneAsync(Object state, Map<String, ? extends Question<?>> questions) {
        return systemOneAsync(SystemOneRequest.builder().state(state).questions(questions).build());
    }

    public CompletableFuture<SystemOneResponse> systemOneAsync(SystemOneRequest request) {
        Map<String, Object> body;
        try {
            body = request.toWire(defaultModel);
        } catch (TypeSafeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return transport.send("POST", SYSTEM_ONE_PATH, body, request.options())
            .thenApply(json -> decoder.decode(request, json));
    }

    /** Closes the HTTP client if this instance created it. */
    @Override
    public void close() {
        if (ownedHttpClient != null) {
            ownedHttpClient.close();
        }
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private String defaultModel;
        private Duration timeout = DEFAULT_TIMEOUT;
        private RetryPolicy retry = RetryPolicy.defaults();
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private HttpClient httpClient;
        private ObjectMapper objectMapper;
        private DoubleSupplier random = Math::random;

        private Builder() {}

        /** API key; falls back to {@code TYPESAFE_API_KEY}. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** API root; falls back to {@code TYPESAFE_BASE_URL}, then {@value #DEFAULT_BASE_URL}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Default model; falls back to {@code TYPESAFE_DEFAULT_MODEL}, then {@value #DEFAULT_MODEL}. */
        public Builder defaultModel(String model) {
            this.defaultModel = model;
            return this;
        }

        /** Per-attempt timeout; there is no total budget across retries. Default 10s. */
        public Builder timeout(Duration timeout) {
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new TypeSafeException("timeout must be positive.");
            }
            this.timeout = timeout;
            return this;
        }

        public Builder retry(RetryPolicy retry) {
            this.retry = retry == null ? RetryPolicy.defaults() : retry;
            return this;
        }

        /** A header sent with every request. Auth and content headers cannot be overridden. */
        public Builder defaultHeader(String name, String value) {
            defaultHeaders.put(name, value);
            return this;
        }

        /** A custom HTTP client (proxies, executors, TLS). The SDK will not close it. */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /** A custom Jackson mapper used for state, instructions, criteria, and responses. */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /** Randomness source for backoff jitter; for tests. */
        Builder random(DoubleSupplier random) {
            this.random = random;
            return this;
        }

        public TypeSafeClient build() {
            return new TypeSafeClient(this);
        }
    }
}
