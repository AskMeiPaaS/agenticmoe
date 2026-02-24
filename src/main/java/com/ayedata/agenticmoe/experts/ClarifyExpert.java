package com.ayedata.agenticmoe.experts;

import com.ayedata.agenticmoe.core.VoyageClient;
import java.util.concurrent.CompletableFuture;

public final class ClarifyExpert implements Expert {

    @Override
    public CompletableFuture<String> execute(String query, VoyageClient client, java.util.List<String> traceLogs) {
        return CompletableFuture.completedFuture(
                "I'm not quite sure I understand what you're asking. Could you provide a bit more detail or clarify your question?");
    }
}
