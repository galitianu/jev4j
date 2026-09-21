package com.galitianu.jev4j.errors;

/** The request could not be sent or the response could not be read (DNS, TLS, connection reset, ...). */
public class ApiConnectionException extends TypeSafeException {
    public ApiConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
