package com.ayedata.agenticmoe.experts;

import com.ayedata.agenticmoe.core.VoyageClient;
import java.util.concurrent.CompletableFuture;

public final class FinanceExpert implements Expert {
    private final String model;

    public FinanceExpert(String model) {
        this.model = model;
    }

    @Override
    public CompletableFuture<String> execute(String query, VoyageClient client, java.util.List<String> traceLogs) {
        return client.embedQuery(query, model)
                .thenApply(v -> "[FinanceExpert] Analysis completed using " + model);
    }
}