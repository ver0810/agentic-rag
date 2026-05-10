package com.agenticrag.infra.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agenticrag.ai.observability")
public class AiObservabilityProperties {

    private double charsPerToken = 4.0d;
    private Cost cost = new Cost();
    private Alerts alerts = new Alerts();

    public double getCharsPerToken() {
        return charsPerToken;
    }

    public void setCharsPerToken(double charsPerToken) {
        this.charsPerToken = charsPerToken;
    }

    public Cost getCost() {
        return cost;
    }

    public void setCost(Cost cost) {
        this.cost = cost;
    }

    public Alerts getAlerts() {
        return alerts;
    }

    public void setAlerts(Alerts alerts) {
        this.alerts = alerts;
    }

    public static class Cost {
        private double chatInputPer1kTokens = 0.002d;
        private double chatOutputPer1kTokens = 0.008d;
        private double embeddingPer1kTokens = 0.0001d;

        public double getChatInputPer1kTokens() {
            return chatInputPer1kTokens;
        }

        public void setChatInputPer1kTokens(double chatInputPer1kTokens) {
            this.chatInputPer1kTokens = chatInputPer1kTokens;
        }

        public double getChatOutputPer1kTokens() {
            return chatOutputPer1kTokens;
        }

        public void setChatOutputPer1kTokens(double chatOutputPer1kTokens) {
            this.chatOutputPer1kTokens = chatOutputPer1kTokens;
        }

        public double getEmbeddingPer1kTokens() {
            return embeddingPer1kTokens;
        }

        public void setEmbeddingPer1kTokens(double embeddingPer1kTokens) {
            this.embeddingPer1kTokens = embeddingPer1kTokens;
        }
    }

    public static class Alerts {
        private int minimumSampleSize = 5;
        private int consecutiveFailuresThreshold = 3;
        private double retrievalSuccessDropThreshold = 0.15d;
        private double modelErrorRateThreshold = 0.20d;
        private double modelErrorRateIncreaseThreshold = 0.10d;
        private boolean notificationsEnabled = false;
        private String webhookUrl;
        private long dispatchIntervalMs = 300000L;

        public int getMinimumSampleSize() {
            return minimumSampleSize;
        }

        public void setMinimumSampleSize(int minimumSampleSize) {
            this.minimumSampleSize = minimumSampleSize;
        }

        public int getConsecutiveFailuresThreshold() {
            return consecutiveFailuresThreshold;
        }

        public void setConsecutiveFailuresThreshold(int consecutiveFailuresThreshold) {
            this.consecutiveFailuresThreshold = consecutiveFailuresThreshold;
        }

        public double getRetrievalSuccessDropThreshold() {
            return retrievalSuccessDropThreshold;
        }

        public void setRetrievalSuccessDropThreshold(double retrievalSuccessDropThreshold) {
            this.retrievalSuccessDropThreshold = retrievalSuccessDropThreshold;
        }

        public double getModelErrorRateThreshold() {
            return modelErrorRateThreshold;
        }

        public void setModelErrorRateThreshold(double modelErrorRateThreshold) {
            this.modelErrorRateThreshold = modelErrorRateThreshold;
        }

        public double getModelErrorRateIncreaseThreshold() {
            return modelErrorRateIncreaseThreshold;
        }

        public void setModelErrorRateIncreaseThreshold(double modelErrorRateIncreaseThreshold) {
            this.modelErrorRateIncreaseThreshold = modelErrorRateIncreaseThreshold;
        }

        public boolean isNotificationsEnabled() {
            return notificationsEnabled;
        }

        public void setNotificationsEnabled(boolean notificationsEnabled) {
            this.notificationsEnabled = notificationsEnabled;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public long getDispatchIntervalMs() {
            return dispatchIntervalMs;
        }

        public void setDispatchIntervalMs(long dispatchIntervalMs) {
            this.dispatchIntervalMs = dispatchIntervalMs;
        }
    }
}
