package com.galitianu.jev4j;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Retry configuration. Defaults match the JS and Python SDKs: two retries with exponential backoff
 * from 500ms to 5s, 25% jitter, retrying HTTP 408, 429 and 5xx as well as connection errors and
 * timeouts, honoring {@code Retry-After} up to 60s.
 *
 * @param maxRetries           retries after the first attempt; 0 disables retries
 * @param backoffInitial       first backoff delay, doubled each retry up to {@code backoffMax}
 * @param backoffMax           maximum backoff delay
 * @param backoffJitter        fraction of each delay randomly subtracted, from 0 to 1
 * @param httpStatuses         HTTP status codes that are retried
 * @param respectRetryAfter    honor {@code Retry-After} and {@code retry-after-ms} headers
 * @param maxRetryAfter        longest server-requested delay to honor; longer ones fall back to backoff
 * @param retryConnectionErrors retry {@link ApiConnectionException}s
 * @param retryTimeouts        retry {@link ApiTimeoutException}s
 */
public record RetryPolicy(
    int maxRetries,
    Duration backoffInitial,
    Duration backoffMax,
    double backoffJitter,
    Set<Integer> httpStatuses,
    boolean respectRetryAfter,
    Duration maxRetryAfter,
    boolean retryConnectionErrors,
    boolean retryTimeouts) {

    /** HTTP 408, 429 and 500-599. */
    public static final Set<Integer> DEFAULT_HTTP_STATUSES;

    static {
        Set<Integer> s = new HashSet<>();
        s.add(408);
        s.add(429);
        IntStream.range(500, 600).forEach(s::add);
        DEFAULT_HTTP_STATUSES = Collections.unmodifiableSet(s);
    }

    private static final RetryPolicy DEFAULTS = new RetryPolicy(
        2, Duration.ofMillis(500), Duration.ofSeconds(5), 0.25, DEFAULT_HTTP_STATUSES, true, Duration.ofSeconds(60), true, true);

    public RetryPolicy {
        if (maxRetries < 0) {
            throw new TypeSafeException("maxRetries must be >= 0.");
        }
        if (backoffJitter < 0 || backoffJitter > 1) {
            throw new TypeSafeException("backoffJitter must be between 0 and 1.");
        }
        httpStatuses = Collections.unmodifiableSet(new HashSet<>(httpStatuses));
    }

    /** The SDK defaults. */
    public static RetryPolicy defaults() {
        return DEFAULTS;
    }

    /** A policy that never retries. */
    public static RetryPolicy none() {
        return DEFAULTS.withMaxRetries(0);
    }

    public RetryPolicy withMaxRetries(int maxRetries) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, httpStatuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retryTimeouts);
    }

    public RetryPolicy withBackoff(Duration initial, Duration max) {
        return new RetryPolicy(maxRetries, initial, max, backoffJitter, httpStatuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retryTimeouts);
    }

    public RetryPolicy withBackoffJitter(double jitter) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, jitter, httpStatuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retryTimeouts);
    }

    public RetryPolicy withHttpStatuses(Set<Integer> statuses) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, statuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retryTimeouts);
    }

    public RetryPolicy withRespectRetryAfter(boolean respect, Duration max) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, httpStatuses, respect, max, retryConnectionErrors, retryTimeouts);
    }

    public RetryPolicy withRetryConnectionErrors(boolean retry) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, httpStatuses, respectRetryAfter, maxRetryAfter, retry, retryTimeouts);
    }

    public RetryPolicy withRetryTimeouts(boolean retry) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, httpStatuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retry);
    }

    /** Whether a status code is retried under this policy. */
    public boolean retries(int status) {
        return httpStatuses.contains(status);
    }

    /** Whether a transport failure is retried under this policy. */
    public boolean retries(Throwable error) {
        if (error instanceof ApiTimeoutException) {
            return retryTimeouts;
        }
        return error instanceof ApiConnectionException && retryConnectionErrors;
    }
}
