package com.galitianu.jev4j;

/**
 * SDK version constants.
 *
 * <p>Generated from the Gradle build; edit
 * {@code src/main/templates/java/com/galitianu/jev4j/Version.java}, not the copy under
 * {@code build/}. The version itself lives in {@code build.gradle.kts} and has no second home.
 */
public final class Version {
    private Version() {}

    /** The SDK version. */
    public static final String VERSION = "@version@";

    /** Value sent as {@code User-Agent} and {@code X-TypeSafe-SDK}. */
    public static final String USER_AGENT = "jev4j/" + VERSION;
}
