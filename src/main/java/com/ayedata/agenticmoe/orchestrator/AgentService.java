package com.ayedata.agenticmoe.orchestrator;

import jakarta.annotation.PostConstruct;
import com.ayedata.agenticmoe.config.AppConfig;
import com.ayedata.agenticmoe.core.VoyageClient;
import com.ayedata.agenticmoe.experts.*;
import com.ayedata.agenticmoe.model.ExpertType;
import com.ayedata.agenticmoe.router.SemanticRouter;
import org.springframework.stereotype.Service;
import com.ayedata.agenticmoe.cache.SemanticCache;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {
    private final VoyageClient client;
    private final SemanticRouter router;
    private final AppConfig config;
    private final SemanticCache cache;

    public record AgentResponse(String answer, ExpertType expertUsed, Map<ExpertType, Double> routingScores,
            long routingLatencyMs, long executionLatencyMs) {
    }

    public AgentService(AppConfig config, VoyageClient client, SemanticCache cache) {
        this.config = config;
        this.client = client;
        this.cache = cache;
        this.router = new SemanticRouter(config.getRouting().getThreshold(),
                config.getRouting().getClarificationThreshold(), config.getRouting().getDimension(),
                client);
    }

    @PostConstruct
    public void init() {
        try {
            this.router.initializeSkillMap().join();
        } catch (Exception e) {
            System.err.println("[AgentService] Failed to initialize Router Skill Map: " + e.getMessage());
        }
    }

    public CompletableFuture<AgentResponse> processQueryAsync(String query) {
        List<String> traceLogs = new CopyOnWriteArrayList<>();

        // TIER 1: Optimized Routing
        long routingStart = System.currentTimeMillis();
        return client.embedQuery(query, config.getRouting().getModel(), config.getRouting().getDimension(), traceLogs)
                .thenCompose(vec -> {
                    long routingLatencyMs = System.currentTimeMillis() - routingStart;
                    SemanticRouter.RoutingResult routingResult = router.route(vec, traceLogs);
                    ExpertType type = routingResult.expert();

                    // TIER 2: Expert Execution
                    Expert expert = switch (type) {
                        case CODE -> new CodeExpert(config.getExperts().getCodeModel());
                        case FINANCE -> new FinanceExpert(config.getExperts().getFinanceModel());
                        case GENERAL -> new GeneralistExpert(config.getExperts().getGeneralModel());
                        case CLARIFY -> new ClarifyExpert();
                    };

                    long executionStart = System.currentTimeMillis();
                    return expert.execute(query, client, traceLogs)
                            .thenApply(ans -> {
                                long executionLatencyMs = System.currentTimeMillis() - executionStart;
                                cache.put(query, vec, config.getRouting().getModel(), ans, type.toString(),
                                        routingLatencyMs, executionLatencyMs, traceLogs);
                                cache.putExpertSkill(query, type, routingResult.expertEmbedding());
                                return new AgentResponse(ans, type, routingResult.scores(), routingLatencyMs,
                                        executionLatencyMs);
                            });
                })
                .exceptionally(ex -> {
                    return new AgentResponse(
                            "error while coordinating with AI services. Fallback enabled. Error: " + ex.getMessage(),
                            ExpertType.GENERAL, java.util.Map.of(), 0, 0);
                });
    }
}