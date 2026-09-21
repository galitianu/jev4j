package com.galitianu.jev4j.internal;

import com.galitianu.jev4j.errors.TypeSafeException;

/** Validation of question keys. */
public final class Keys {
    private Keys() {}

    public static String requireValid(String name) {
        if (name == null || name.isBlank()) {
            throw new TypeSafeException("Question name must not be blank.");
        }
        return name;
    }
}
