package com.galitianu.jev4j;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Per-call overrides for timeout, retry policy, and headers. */
public final class RequestOptions {

    private static final RequestOptions NONE = new RequestOptions(null, null, Map.of());

    private final Duration timeout;
    private final RetryPolicy retry;
    private final Map<String, String> headers;

    private RequestOptions(Duration timeout, RetryPolicy retry, Map<String, String> headers) {
        this.timeout = timeout;
        this.retry = retry;
        this.headers = headers;
    }

    /** No overrides. */
    public static RequestOptions none() {
        return NONE;
    }

    /** Per-attempt timeout for this call. */
    public RequestOptions withTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new TypeSafeException("timeout must be positive.");
        }
        return new RequestOptions(timeout, retry, headers);
    }

    /** Retry policy for this call. */
    public RequestOptions withRetry(RetryPolicy retry) {
        return new RequestOptions(timeout, retry, headers);
    }

    /** An extra header for this call; takes precedence over client default headers. */
    public RequestOptions withHeader(String name, String value) {
        Map<String, String> copy = new LinkedHashMap<>(headers);
        copy.put(name, value);
        return new RequestOptions(timeout, retry, Collections.unmodifiableMap(copy));
    }

    public Optional<Duration> timeout() {
        return Optional.ofNullable(timeout);
    }

    public Optional<RetryPolicy> retry() {
        return Optional.ofNullable(retry);
    }

    public Map<String, String> headers() {
        return headers;
    }
}
