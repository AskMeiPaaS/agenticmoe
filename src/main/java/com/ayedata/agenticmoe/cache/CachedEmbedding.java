package com.ayedata.agenticmoe.cache;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.Instant;

/**
 * Represents a cached vector embedding in MongoDB.
 */
@Document(collection = "embedding_cache")
public record CachedEmbedding(
                @Id String id,
                @Indexed String inputText, // The raw user input
                float[] vector, // The numerical query embedding
                String model, // Model ID (e.g., voyage-4-nano)
                String response, // The system response
                @Indexed String expertUsed, // The expert used (e.g., CODE, FINANCE, GENERAL)
                Long routingLatencyMs, // Routing API call latency
                Long executionLatencyMs, // Expert execution API call latency
                java.util.List<String> traceLogs, // Internal traces
                @Indexed(expireAfter = "30d") Instant createdAt // TTL: Auto-delete after 30 days
) {
}