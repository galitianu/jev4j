package com.galitianu.jev4j.internal;

import com.galitianu.jev4j.errors.TypeSafeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Blocking on futures while surfacing SDK exceptions unwrapped. */
public final class Futures {
    private Futures() {}

    public static <T> T join(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new TypeSafeException("Interrupted while waiting for the TypeSafe API.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TypeSafeException t) {
                throw t;
            }
            throw new TypeSafeException(cause == null ? e.getMessage() : cause.toString(), cause == null ? e : cause);
        }
    }
}
