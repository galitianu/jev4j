package com.galitianu.jev4j;

/** SDK version constants. */
public final class Version {
    private Version() {}

    /** The SDK version, kept in sync with the build. */
    public static final String VERSION = "0.1.0-SNAPSHOT";

    /** Value sent as {@code User-Agent} and {@code X-TypeSafe-SDK}. */
    public static final String USER_AGENT = "jev4j/" + VERSION;
}
