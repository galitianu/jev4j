package com.galitianu.jev4j;

/** Validation of question keys. */
final class Keys {
    private Keys() {}

    static String requireValid(String name) {
        if (name == null || name.isBlank()) {
            throw new TypeSafeException("Question name must not be blank.");
        }
        return name;
    }
}
