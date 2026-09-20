package com.galitianu.jev4j;

/** Base class for all SDK exceptions. */
public class TypeSafeException extends RuntimeException {
    public TypeSafeException(String message) {
        super(message);
    }

    public TypeSafeException(String message, Throwable cause) {
        super(message, cause);
    }
}
