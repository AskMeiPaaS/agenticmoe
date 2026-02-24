package com.ayedata.agenticmoe.experts;

import com.ayedata.agenticmoe.core.VoyageClient;
import java.util.concurrent.CompletableFuture;

public final class CodeExpert implements Expert {
    private final String model;

    // Add this constructor
    public CodeExpert(String model) {
        this.model = model;
    }

    @Override
    public CompletableFuture<String> execute(String query, VoyageClient client, java.util.List<String> traceLogs) {
        // Use the model provided by the config
        return client.embedQuery(query, model)
                .thenApply(v -> "[CodeExpert] Logic executed using " + model);
    }
}