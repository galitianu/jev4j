package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Optional;

/** HTTP 429: the rate limit was exceeded. */
public class RateLimitException extends ApiException {
    protected RateLimitException(int status, JsonNode body, HttpHeaders headers) {
        super(status, body, headers);
    }

    /** The server's requested retry delay from {@code retry-after-ms} or {@code Retry-After}, when present. */
    public Optional<Duration> retryAfter() {
        return RetryDelays.parseRetryAfter(headers());
    }
}
