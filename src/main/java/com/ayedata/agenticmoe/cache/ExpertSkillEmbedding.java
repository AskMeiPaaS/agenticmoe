package com.ayedata.agenticmoe.cache;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import com.ayedata.agenticmoe.model.ExpertType;

/**
 * Represents the best match expert for a specific user query, stored in
 * MongoDB.
 */
@Document(collection = "expert_skills")
public record ExpertSkillEmbedding(
        @Id String id,
        @Indexed String inputQuery, // The raw user input
        @Indexed ExpertType expertType, // The expert enum (CODE, FINANCE, GENERAL)
        float[] expertEmbedding // The best matching expert embedding
) {
}
