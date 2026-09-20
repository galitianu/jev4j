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

    /**
     * Validates the policy and defensively copies {@code httpStatuses}, so the set passed in
     * cannot be mutated afterwards.
     *
     * @throws TypeSafeException if {@code maxRetries} is negative, or {@code backoffJitter} is
     *                           outside 0 to 1
     */
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

    /**
     * Returns a copy with a different retry count.
     *
     * @param maxRetries retries after the first attempt; 0 disables retries
     * @return a new policy; this one is left unchanged
     * @throws TypeSafeException if {@code maxRetries} is negative
     */
    public RetryPolicy withMaxRetries(int maxRetries) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, httpStatuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retryTimeouts);
    }

    /**
     * Returns a copy with different backoff bounds. The delay starts at {@code initial} and
     * doubles on each retry until it reaches {@code max}; jitter is applied afterwards.
     *
     * @param initial first backoff delay
     * @param max     longest backoff delay
     * @return a new policy; this one is left unchanged
     */
    public RetryPolicy withBackoff(Duration initial, Duration max) {
        return new RetryPolicy(maxRetries, initial, max, backoffJitter, httpStatuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retryTimeouts);
    }

    /**
     * Returns a copy with a different jitter fraction. Each delay has up to this fraction of
     * itself randomly subtracted, so that clients retrying together spread out rather than
     * hitting the API in step.
     *
     * @param jitter fraction of each delay to subtract, from 0 (no jitter) to 1
     * @return a new policy; this one is left unchanged
     * @throws TypeSafeException if {@code jitter} is outside 0 to 1
     */
    public RetryPolicy withBackoffJitter(double jitter) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, jitter, httpStatuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retryTimeouts);
    }

    /**
     * Returns a copy that retries a different set of status codes, replacing
     * {@link #DEFAULT_HTTP_STATUSES} rather than adding to it.
     *
     * @param statuses HTTP status codes to retry
     * @return a new policy; this one is left unchanged
     */
    public RetryPolicy withHttpStatuses(Set<Integer> statuses) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, statuses, respectRetryAfter, maxRetryAfter, retryConnectionErrors, retryTimeouts);
    }

    /**
     * Returns a copy with different handling of server-requested delays.
     *
     * @param respect whether to honor {@code Retry-After} and {@code retry-after-ms} headers
     * @param max     longest delay to honor; the server asking for longer falls back to backoff
     * @return a new policy; this one is left unchanged
     */
    public RetryPolicy withRespectRetryAfter(boolean respect, Duration max) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, httpStatuses, respect, max, retryConnectionErrors, retryTimeouts);
    }

    /**
     * Returns a copy that does or does not retry {@link ApiConnectionException}s.
     *
     * @param retry whether to retry DNS, TLS and connection failures
     * @return a new policy; this one is left unchanged
     */
    public RetryPolicy withRetryConnectionErrors(boolean retry) {
        return new RetryPolicy(maxRetries, backoffInitial, backoffMax, backoffJitter, httpStatuses, respectRetryAfter, maxRetryAfter, retry, retryTimeouts);
    }

    /**
     * Returns a copy that does or does not retry {@link ApiTimeoutException}s.
     *
     * @param retry whether to retry attempts that exceeded the timeout
     * @return a new policy; this one is left unchanged
     */
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
