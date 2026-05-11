package com.agenticrag.infra.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agenticrag.ai.rag")
public class RagProperties {

    private boolean hybridEnabled = true;
    private int defaultTopK = 5;
    private int vectorTopK = 12;
    private int keywordTopK = 8;
    private double similarityThreshold = 0.55d;
    private double vectorWeight = 0.75d;
    private double keywordWeight = 0.25d;
    private int rrfK = 60;
    private boolean rewriteEnabled = true;
    private boolean multiQueryEnabled = false;
    private int multiQueryCount = 3;
    private boolean rerankEnabled = true;
    private String rerankerType = "lexical";
    private String rerankerApiUrl;
    private String rerankerApiKey;
    private String rerankerModel;
    private double rerankerRetrievalWeight = 0.8d;
    private double rerankerLexicalWeight = 0.2d;
    private int maxContextChunks = 6;
    private String promptTemplate;

    public boolean isHybridEnabled() {
        return hybridEnabled;
    }

    public void setHybridEnabled(boolean hybridEnabled) {
        this.hybridEnabled = hybridEnabled;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public int getVectorTopK() {
        return vectorTopK;
    }

    public void setVectorTopK(int vectorTopK) {
        this.vectorTopK = vectorTopK;
    }

    public int getKeywordTopK() {
        return keywordTopK;
    }

    public void setKeywordTopK(int keywordTopK) {
        this.keywordTopK = keywordTopK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public double getKeywordWeight() {
        return keywordWeight;
    }

    public void setKeywordWeight(double keywordWeight) {
        this.keywordWeight = keywordWeight;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }

    public boolean isRewriteEnabled() {
        return rewriteEnabled;
    }

    public void setRewriteEnabled(boolean rewriteEnabled) {
        this.rewriteEnabled = rewriteEnabled;
    }

    public boolean isMultiQueryEnabled() {
        return multiQueryEnabled;
    }

    public void setMultiQueryEnabled(boolean multiQueryEnabled) {
        this.multiQueryEnabled = multiQueryEnabled;
    }

    public int getMultiQueryCount() {
        return multiQueryCount;
    }

    public void setMultiQueryCount(int multiQueryCount) {
        this.multiQueryCount = multiQueryCount;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public void setRerankEnabled(boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
    }

    public String getRerankerType() {
        return rerankerType;
    }

    public void setRerankerType(String rerankerType) {
        this.rerankerType = rerankerType;
    }

    public String getRerankerApiUrl() {
        return rerankerApiUrl;
    }

    public void setRerankerApiUrl(String rerankerApiUrl) {
        this.rerankerApiUrl = rerankerApiUrl;
    }

    public String getRerankerApiKey() {
        return rerankerApiKey;
    }

    public void setRerankerApiKey(String rerankerApiKey) {
        this.rerankerApiKey = rerankerApiKey;
    }

    public String getRerankerModel() {
        return rerankerModel;
    }

    public void setRerankerModel(String rerankerModel) {
        this.rerankerModel = rerankerModel;
    }

    public double getRerankerRetrievalWeight() {
        return rerankerRetrievalWeight;
    }

    public void setRerankerRetrievalWeight(double rerankerRetrievalWeight) {
        this.rerankerRetrievalWeight = rerankerRetrievalWeight;
    }

    public double getRerankerLexicalWeight() {
        return rerankerLexicalWeight;
    }

    public void setRerankerLexicalWeight(double rerankerLexicalWeight) {
        this.rerankerLexicalWeight = rerankerLexicalWeight;
    }

    public int getMaxContextChunks() {
        return maxContextChunks;
    }

    public void setMaxContextChunks(int maxContextChunks) {
        this.maxContextChunks = maxContextChunks;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }
}
