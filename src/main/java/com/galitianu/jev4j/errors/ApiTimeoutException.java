package com.galitianu.jev4j.errors;

import java.time.Duration;

/** The full response did not arrive within the per-attempt timeout. */
public class ApiTimeoutException extends ApiConnectionException {
    private final Duration timeout;

    public ApiTimeoutException(Duration timeout, Throwable cause) {
        super("Request timed out after " + timeout.toMillis() + "ms.", cause);
        this.timeout = timeout;
    }

    /** The configured per-attempt timeout. */
    public Duration timeout() {
        return timeout;
    }
}
