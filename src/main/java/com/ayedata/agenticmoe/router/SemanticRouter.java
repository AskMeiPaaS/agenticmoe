package com.ayedata.agenticmoe.router;

import com.ayedata.agenticmoe.core.VectorMath;
import com.ayedata.agenticmoe.core.VoyageClient;
import com.ayedata.agenticmoe.model.ExpertType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

public class SemanticRouter {
    private final Map<ExpertType, float[]> expertEmbeddings = new ConcurrentHashMap<>();
    private final double threshold;
    private final double clarificationThreshold;
    private final int dimension;
    private final VoyageClient voyageClient;

    public SemanticRouter(double threshold, double clarificationThreshold, int dimension, VoyageClient voyageClient) {
        this.threshold = threshold;
        this.clarificationThreshold = clarificationThreshold;
        this.dimension = dimension;
        this.voyageClient = voyageClient;
    }

    /**
     * Dynamically loads the skill map by embedding the expert descriptions
     * using the full Voyage model, then applies binary quantization to the
     * vectors for fast Tier 1 routing.
     */
    public CompletableFuture<Void> initializeSkillMap() {
        String indexModel = "voyage-4-large";

        System.out.println("[SemanticRouter] Initializing Dynamic Skill Map...");

        var codeFuture = voyageClient.embedQuery(
                "Expert in technical syntax, programming languages, APIs, code architecture, and debugging.",
                indexModel, dimension)
                .thenAccept(vec -> expertEmbeddings.put(ExpertType.CODE, vec));

        var financeFuture = voyageClient.embedQuery(
                "Expert in financial terms, regulatory compliance, budgeting, trading, and corporate finance.",
                indexModel, dimension)
                .thenAccept(vec -> expertEmbeddings.put(ExpertType.FINANCE, vec));

        var generalFuture = voyageClient.embedQuery(
                "Generalist expert for summaries, broad questions, casual conversation, and basic facts.",
                indexModel, dimension)
                .thenAccept(vec -> expertEmbeddings.put(ExpertType.GENERAL, vec));

        return CompletableFuture.allOf(codeFuture, financeFuture, generalFuture)
                .thenRun(() -> System.out.println("[SemanticRouter] Skill Map Initialized Successfully."))
                .exceptionally(ex -> {
                    System.err.println("[SemanticRouter] Failed to init skill map: " + ex.getMessage());
                    return null;
                });
    }

    public record RoutingResult(ExpertType expert, Map<ExpertType, Double> scores, float[] expertEmbedding) {
    }

    public RoutingResult route(float[] rawQueryVector, List<String> traceLogs) {
        float[] queryVector = rawQueryVector;
        ExpertType bestMatch = ExpertType.GENERAL;
        double maxScore = -1.0;
        Map<ExpertType, Double> scores = new ConcurrentHashMap<>();

        log("[SemanticRouter] Routing Query... Threshold is: " + threshold, traceLogs);

        for (var entry : expertEmbeddings.entrySet()) {
            double score = VectorMath.cosineSimilarity(queryVector, entry.getValue());
            scores.put(entry.getKey(), score);
            log("[SemanticRouter] Expert: " + entry.getKey() + " | Score: " + score, traceLogs);
            if (score > maxScore) {
                maxScore = score;
                bestMatch = entry.getKey();
            }
        }

        float[] topEmbedding = expertEmbeddings.get(bestMatch);

        log("[SemanticRouter] Max Score: " + maxScore + " | Best Match: " + bestMatch, traceLogs);
        if (maxScore < clarificationThreshold) {
            return new RoutingResult(ExpertType.CLARIFY, scores, topEmbedding);
        } else if (maxScore < threshold) {
            return new RoutingResult(ExpertType.GENERAL, scores, topEmbedding);
        }

        return new RoutingResult(bestMatch, scores, topEmbedding);
    }

    private void log(String message, List<String> traceLogs) {
        System.out.println(message);
        if (traceLogs != null) {
            traceLogs.add(message);
        }
    }

}