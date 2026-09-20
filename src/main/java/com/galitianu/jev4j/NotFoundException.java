package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpHeaders;

/** HTTP 404: the resource was not found. */
public class NotFoundException extends ApiException {
    protected NotFoundException(int status, JsonNode body, HttpHeaders headers) {
        super(status, body, headers);
    }
}
