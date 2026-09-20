package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpHeaders;

/** HTTP 422: request validation failed. */
public class UnprocessableEntityException extends ApiException {
    protected UnprocessableEntityException(int status, JsonNode body, HttpHeaders headers) {
        super(status, body, headers);
    }
}
