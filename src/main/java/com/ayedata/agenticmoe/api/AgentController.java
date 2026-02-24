package com.ayedata.agenticmoe.api;

import com.ayedata.agenticmoe.orchestrator.AgentService;
import com.ayedata.agenticmoe.cache.SemanticCache;
import com.ayedata.agenticmoe.cache.CachedEmbedding;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/agent")
@CrossOrigin(origins = "*") // Allow Node.js frontend to access
public class AgentController {

    private final AgentService agentService;
    private final SemanticCache cache;

    public AgentController(AgentService agentService, SemanticCache cache) {
        this.agentService = agentService;
        this.cache = cache;
    }

    @PostMapping("/chat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        }

        // Async non-blocking response using Virtual Threads (via Service)
        return agentService.processQueryAsync(query)
                .thenApply(response -> ResponseEntity.ok(Map.of(
                        "query", query,
                        "response", response.answer(),
                        "expertUsed", response.expertUsed().toString(),
                        "routingScores", response.routingScores(),
                        "routingLatencyMs", response.routingLatencyMs(),
                        "executionLatencyMs", response.executionLatencyMs(),
                        "cost", "0.00" // Mock cost tracking
                )));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history() {
        try {
            List<CachedEmbedding> recent = cache.getRecentHistory(10);
            List<Map<String, String>> response = recent.stream().map(embed -> {
                Map<String, String> map = new java.util.HashMap<>();
                map.put("query", embed.inputText() != null ? embed.inputText() : "Unknown");
                map.put("response", embed.response() != null ? embed.response() : "");
                map.put("expertUsed", embed.expertUsed() != null ? embed.expertUsed() : "");
                return map;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(e.toString() + " - " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Integer>> stats() {
        return ResponseEntity.ok(cache.getExpertUsageStats());
    }
}