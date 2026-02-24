package com.ayedata.agenticmoe.core;

public class VectorMath {
    /**
     * Converts a standard float array embedding into a binary quantized
     * representation.
     * Positive values become 1.0f, negative values become -1.0f.
     */
    public static float[] quantizeToBinary(float[] vector) {
        float[] binary = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            binary[i] = vector[i] > 0 ? 1.0f : -1.0f;
        }
        return binary;
    }

    /**
     * Computes the cosine similarity between two float arrays.
     */
    public static double cosineSimilarity(float[] vecA, float[] vecB) {
        double dot = 0.0, nA = 0.0, nB = 0.0;
        int len = Math.min(vecA.length, vecB.length);
        for (int i = 0; i < len; i++) {
            dot += vecA[i] * vecB[i];
            nA += vecA[i] * vecA[i];
            nB += vecB[i] * vecB[i];
        }
        return (nA == 0 || nB == 0) ? 0 : dot / (Math.sqrt(nA) * Math.sqrt(nB));
    }
}
