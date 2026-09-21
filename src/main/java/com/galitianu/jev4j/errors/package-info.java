/**
 * Everything the SDK throws.
 *
 * <p>{@link com.galitianu.jev4j.errors.TypeSafeException} is the root, so a single catch covers
 * the SDK. Below it, {@link com.galitianu.jev4j.errors.ApiConnectionException} means the request
 * never produced a response, and {@link com.galitianu.jev4j.errors.ApiException} means the server
 * answered with an error status; the subclass identifies which status.
 */
package com.galitianu.jev4j.errors;
