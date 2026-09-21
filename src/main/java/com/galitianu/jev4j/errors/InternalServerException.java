package com.galitianu.jev4j.errors;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpHeaders;

/** HTTP 5xx: the server failed to handle the request. */
public class InternalServerException extends ApiException {
    protected InternalServerException(int status, JsonNode body, HttpHeaders headers) {
        super(status, body, headers);
    }
}
