package com.ayedata.agenticmoe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "voyage")
public class AppConfig {
    private String apiKey;
    private String baseUrl;
    private String embeddingUrl;
    private String chatUrl;
    private final Routing routing = new Routing();
    private final Experts experts = new Experts();

    public static class Routing {
        private String model;
        private double threshold;
        private double clarificationThreshold;
        private int dimension;

        public String getModel() {
            return model;
        }

        public void setModel(String m) {
            this.model = m;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double t) {
            this.threshold = t;
        }

        public double getClarificationThreshold() {
            return clarificationThreshold;
        }

        public void setClarificationThreshold(double t) {
            this.clarificationThreshold = t;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int d) {
            this.dimension = d;
        }
    }

    public static class Experts {
        private String codeModel;
        private String financeModel;
        private String generalModel;

        public String getCodeModel() {
            return codeModel;
        }

        public void setCodeModel(String m) {
            this.codeModel = m;
        }

        public String getFinanceModel() {
            return financeModel;
        }

        public void setFinanceModel(String m) {
            this.financeModel = m;
        }

        public String getGeneralModel() {
            return generalModel;
        }

        public void setGeneralModel(String m) {
            this.generalModel = m;
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String s) {
        this.apiKey = s;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String s) {
        this.baseUrl = s;
    }

    public String getEmbeddingUrl() {
        return embeddingUrl;
    }

    public void setEmbeddingUrl(String s) {
        this.embeddingUrl = s;
    }

    public String getChatUrl() {
        return chatUrl;
    }

    public void setChatUrl(String s) {
        this.chatUrl = s;
    }

    public Routing getRouting() {
        return routing;
    }

    public Experts getExperts() {
        return experts;
    }
}