package com.ayedata.agenticmoe.experts;

import com.ayedata.agenticmoe.core.VoyageClient;
import java.util.concurrent.CompletableFuture;

public sealed interface Expert permits CodeExpert, FinanceExpert, GeneralistExpert, ClarifyExpert {
    CompletableFuture<String> execute(String query, VoyageClient client, java.util.List<String> traceLogs);
}