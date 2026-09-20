package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The Models resource: {@code GET /v1/models}. */
public final class Models {

    static final String PATH = "/v1/models";

    private final Transport transport;

    Models(Transport transport) {
        this.transport = transport;
    }

    /** Lists the models available to the account. */
    public List<ModelCard> list() {
        return list(RequestOptions.none());
    }

    public List<ModelCard> list(RequestOptions options) {
        return Futures.join(listAsync(options));
    }

    public CompletableFuture<List<ModelCard>> listAsync() {
        return listAsync(RequestOptions.none());
    }

    public CompletableFuture<List<ModelCard>> listAsync(RequestOptions options) {
        return transport.send("GET", PATH, null, options).thenApply(Models::unwrap);
    }

    private static List<ModelCard> unwrap(JsonNode wire) {
        JsonNode models = wire == null ? null : wire.get("models");
        if (models == null || !models.isArray()) {
            throw new TypeSafeException("Unexpected response shape from GET /v1/models; expected { \"models\": [...] }.");
        }
        List<ModelCard> cards = new ArrayList<>();
        for (JsonNode m : models) {
            cards.add(new ModelCard(m.path("name").asText(null), m.path("description").asText(null), m.path("release_date").asText(null)));
        }
        return Collections.unmodifiableList(cards);
    }
}
