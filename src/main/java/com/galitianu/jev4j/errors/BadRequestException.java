package com.galitianu.jev4j.errors;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpHeaders;

/** HTTP 400: the request is invalid. */
public class BadRequestException extends ApiException {
    protected BadRequestException(int status, JsonNode body, HttpHeaders headers) {
        super(status, body, headers);
    }
}
