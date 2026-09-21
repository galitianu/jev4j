package com.galitianu.jev4j.errors;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpHeaders;
import java.util.Iterator;
import java.util.Optional;

/** A non-2xx response from the API, after retries were exhausted or not applicable. */
public class ApiException extends TypeSafeException {

    /** Header carrying the server-side request ID. */
    public static final String REQUEST_ID_HEADER = "x-typesafe-request-id";

    private static final int MAX_RAW_BODY_IN_MESSAGE = 200;

    private final int status;
    private final transient JsonNode body;
    private final transient HttpHeaders headers;

    protected ApiException(int status, JsonNode body, HttpHeaders headers) {
        super(describe(status, body));
        this.status = status;
        this.body = body;
        this.headers = headers;
    }

    /** HTTP status code. */
    public int status() {
        return status;
    }

    /** Parsed JSON body, a text node for non-JSON bodies, or {@code null} for an empty body. */
    public JsonNode body() {
        return body;
    }

    /** Response headers. */
    public HttpHeaders headers() {
        return headers;
    }

    /** Server request ID from {@value #REQUEST_ID_HEADER}, when present. */
    public Optional<String> requestId() {
        return headers.firstValue(REQUEST_ID_HEADER);
    }

    /** Creates the subclass matching an HTTP status code. */
    public static ApiException fromResponse(int status, JsonNode body, HttpHeaders headers) {
        return switch (status) {
            case 400 -> new BadRequestException(status, body, headers);
            case 401 -> new AuthenticationException(status, body, headers);
            case 403 -> new PermissionDeniedException(status, body, headers);
            case 404 -> new NotFoundException(status, body, headers);
            case 422 -> new UnprocessableEntityException(status, body, headers);
            case 429 -> new RateLimitException(status, body, headers);
            default -> status >= 500 ? new InternalServerException(status, body, headers) : new ApiException(status, body, headers);
        };
    }

    private static String describe(int status, JsonNode body) {
        String detail = extractMessage(body);
        if (detail != null) {
            return status + " " + detail;
        }
        if (body == null || body.isMissingNode() || body.isNull()) {
            return status + " status code (no body)";
        }
        String raw = body.isTextual() ? body.asText() : body.toString();
        return status + " " + (raw.length() > MAX_RAW_BODY_IN_MESSAGE ? raw.substring(0, MAX_RAW_BODY_IN_MESSAGE) + "…" : raw);
    }

    /** Extracts a message from a text, error, or validation body, mirroring the JS SDK. */
    static String extractMessage(JsonNode body) {
        if (body == null) {
            return null;
        }
        if (body.isTextual()) {
            return body.asText().isEmpty() ? null : body.asText();
        }
        if (!body.isObject()) {
            return null;
        }
        JsonNode error = body.get("error");
        if (error != null && error.isTextual()) {
            return error.asText();
        }
        if (error != null && error.isObject() && error.path("message").isTextual()) {
            return error.get("message").asText();
        }
        if (body.path("message").isTextual()) {
            return body.get("message").asText();
        }
        JsonNode detail = body.get("detail");
        if (detail != null && detail.isTextual()) {
            return detail.asText();
        }
        if (detail != null && detail.isObject() && detail.path("message").isTextual()) {
            return detail.get("message").asText();
        }
        if (detail != null && detail.isArray()) {
            return describeValidationErrors(detail);
        }
        return null;
    }

    private static String describeValidationErrors(JsonNode errors) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode e : errors) {
            if (!e.isObject() || !e.path("msg").isTextual()) {
                continue;
            }
            StringBuilder loc = new StringBuilder();
            if (e.path("loc").isArray()) {
                Iterator<JsonNode> it = e.get("loc").elements();
                while (it.hasNext()) {
                    String part = it.next().asText();
                    if ("body".equals(part)) {
                        continue;
                    }
                    if (!loc.isEmpty()) {
                        loc.append('.');
                    }
                    loc.append(part);
                }
            }
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            if (!loc.isEmpty()) {
                sb.append(loc).append(": ");
            }
            sb.append(e.get("msg").asText());
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
