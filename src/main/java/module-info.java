/**
 * Java SDK for the TypeSafe AI API.
 *
 * <p>{@code com.galitianu.jev4j} holds the client and the question and answer types;
 * {@code com.galitianu.jev4j.errors} holds the exception hierarchy. Everything else is
 * implementation detail and is deliberately not exported.
 */
module com.galitianu.jev4j {
    requires transitive com.fasterxml.jackson.databind;
    requires transitive java.net.http;

    exports com.galitianu.jev4j;
    exports com.galitianu.jev4j.errors;
}
