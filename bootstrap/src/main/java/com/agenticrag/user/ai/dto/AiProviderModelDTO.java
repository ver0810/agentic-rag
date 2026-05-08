package com.agenticrag.user.ai.dto;

public class AiProviderModelDTO {

    private String modelCode;

    private String displayName;

    private String modelType;

    private Boolean recommended;

    public AiProviderModelDTO() {
    }

    public AiProviderModelDTO(String modelCode, String displayName, String modelType, Boolean recommended) {
        this.modelCode = modelCode;
        this.displayName = displayName;
        this.modelType = modelType;
        this.recommended = recommended;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Boolean getRecommended() {
        return recommended;
    }

    public void setRecommended(Boolean recommended) {
        this.recommended = recommended;
    }
}
