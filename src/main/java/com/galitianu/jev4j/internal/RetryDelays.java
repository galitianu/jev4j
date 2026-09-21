package com.galitianu.jev4j.internal;

import com.galitianu.jev4j.RetryPolicy;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** Retry delay calculation. */
public final class RetryDelays {
    private RetryDelays() {}

    /** Parses {@code retry-after-ms} or {@code Retry-After} (seconds or HTTP date), preferring the former. */
    public static Optional<Duration> parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return Optional.empty();
        }
        Optional<String> ms = headers.firstValue("retry-after-ms");
        if (ms.isPresent()) {
            try {
                long v = Long.parseLong(ms.get().trim());
                if (v >= 0) {
                    return Optional.of(Duration.ofMillis(v));
                }
            } catch (NumberFormatException ignored) {
                // fall through to Retry-After
            }
        }
        Optional<String> raw = headers.firstValue("retry-after");
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        String value = raw.get().trim();
        try {
            double seconds = Double.parseDouble(value);
            return seconds >= 0 ? Optional.of(Duration.ofMillis(Math.round(seconds * 1000))) : Optional.empty();
        } catch (NumberFormatException ignored) {
            // maybe an HTTP date
        }
        try {
            ZonedDateTime date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration until = Duration.between(ZonedDateTime.now(date.getZone()), date);
            return Optional.of(until.isNegative() ? Duration.ZERO : until);
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    /** Delay before the zero-based retry {@code attempt}: the server's delay if allowed, else capped backoff with jitter. */
    public static Duration delayFor(int attempt, HttpHeaders headers, RetryPolicy policy, DoubleSupplier random) {
        if (policy.respectRetryAfter() && headers != null) {
            Optional<Duration> retryAfter = parseRetryAfter(headers);
            if (retryAfter.isPresent() && retryAfter.get().compareTo(policy.maxRetryAfter()) <= 0) {
                return retryAfter.get();
            }
        }
        double exponential = Math.min(policy.backoffInitial().toMillis() * Math.pow(2, attempt), policy.backoffMax().toMillis());
        return Duration.ofMillis(Math.round(exponential * (1 - random.getAsDouble() * policy.backoffJitter())));
    }
}
