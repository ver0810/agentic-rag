package com.agenticrag.infra.ai.model;

public enum AiChatScene {
    GENERAL("general"),
    RAG_QA("rag_qa"),
    SUMMARY("summary"),
    TITLE_GENERATION("title_generation");

    private final String code;

    AiChatScene(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AiChatScene fromCode(String code) {
        if (code == null || code.isBlank()) {
            return GENERAL;
        }
        for (AiChatScene scene : values()) {
            if (scene.code.equalsIgnoreCase(code)) {
                return scene;
            }
        }
        throw new IllegalArgumentException("Unsupported ai chat scene: " + code);
    }
}
