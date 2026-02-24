package com.ayedata.agenticmoe.experts;

import com.ayedata.agenticmoe.core.VoyageClient;
import java.util.concurrent.CompletableFuture;

public final class GeneralistExpert implements Expert {
    private final String model;

    public GeneralistExpert(String model) {
        this.model = model;
    }

    @Override
    public CompletableFuture<String> execute(String query, VoyageClient client, java.util.List<String> traceLogs) {
        return client.embedQuery(query, model)
                .thenApply(v -> "[GeneralistExpert] Response generated using " + model);
    }
}