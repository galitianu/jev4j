package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpHeaders;

/** HTTP 401: the API key is missing or invalid. */
public class AuthenticationException extends ApiException {
    protected AuthenticationException(int status, JsonNode body, HttpHeaders headers) {
        super(status, body, headers);
    }
}
