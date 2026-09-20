package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpHeaders;

/** HTTP 403: access is denied. */
public class PermissionDeniedException extends ApiException {
    protected PermissionDeniedException(int status, JsonNode body, HttpHeaders headers) {
        super(status, body, headers);
    }
}
