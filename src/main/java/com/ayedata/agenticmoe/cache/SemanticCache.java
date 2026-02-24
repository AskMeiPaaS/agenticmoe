package com.ayedata.agenticmoe.cache;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;

@Service
public class SemanticCache {

    private final MongoTemplate mongoTemplate;

    public SemanticCache(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Now references the standalone CachedEmbedding class
     */
    public Optional<float[]> get(String query, String model) {
        java.util.Objects.requireNonNull(query, "query must not be null");
        Query q = new Query(org.springframework.data.mongodb.core.query.Criteria.where("inputText").is(query));
        CachedEmbedding cached = mongoTemplate.findOne(q, CachedEmbedding.class);

        if (cached != null && model.equals(cached.model())) {
            return Optional.of(cached.vector());
        }
        return Optional.empty();
    }

    public void put(String inputText, float[] vector, String model, String response, String expertUsed,
            long routingLatencyMs, long executionLatencyMs, List<String> traceLogs) {
        mongoTemplate.save(new CachedEmbedding(
                null,
                inputText,
                vector,
                model,
                response,
                expertUsed,
                routingLatencyMs,
                executionLatencyMs,
                traceLogs,
                Instant.now()));
    }

    public void putExpertSkill(String inputQuery, com.ayedata.agenticmoe.model.ExpertType expertType,
            float[] expertEmbedding) {
        mongoTemplate.save(new ExpertSkillEmbedding(null, inputQuery, expertType, expertEmbedding));
    }

    public List<CachedEmbedding> getRecentHistory(int limit) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, CachedEmbedding.class);
    }

    public java.util.Map<String, Integer> getExpertUsageStats() {
        org.springframework.data.mongodb.core.aggregation.Aggregation aggregation = org.springframework.data.mongodb.core.aggregation.Aggregation
                .newAggregation(
                        org.springframework.data.mongodb.core.aggregation.Aggregation.group("expertUsed").count()
                                .as("count"));

        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> results = mongoTemplate
                .aggregate(aggregation, "embedding_cache", org.bson.Document.class);

        java.util.Map<String, Integer> stats = new java.util.HashMap<>();
        for (org.bson.Document doc : results.getMappedResults()) {
            String expert = doc.getString("_id");
            Integer count = doc.getInteger("count");
            if (expert != null) {
                stats.put(expert, count);
            }
        }
        return stats;
    }
}