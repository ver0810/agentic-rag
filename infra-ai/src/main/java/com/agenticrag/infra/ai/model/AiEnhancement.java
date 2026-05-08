package com.agenticrag.infra.ai.model;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 功能增强配置
 */
public class AiEnhancement {

    /**
     * 安全增强：是否开启 Guardian AI 审核
     */
    private boolean enableGuardian = false;

    /**
     * 上下文增强：注入 project-specific 指令 (类似 CLAUDE.md)
     */
    private String projectContext;

    /**
     * 性能增强：语义缓存开关
     */
    private boolean useSemanticCache = false;

    /**
     * 观察性增强：Trace 标记
     */
    private Map<String, String> traceMetadata = new HashMap<>();

    public boolean isEnableGuardian() {
        return enableGuardian;
    }

    public void setEnableGuardian(boolean enableGuardian) {
        this.enableGuardian = enableGuardian;
    }

    public String getProjectContext() {
        return projectContext;
    }

    public void setProjectContext(String projectContext) {
        this.projectContext = projectContext;
    }

    public boolean isUseSemanticCache() {
        return useSemanticCache;
    }

    public void setUseSemanticCache(boolean useSemanticCache) {
        this.useSemanticCache = useSemanticCache;
    }

    public Map<String, String> getTraceMetadata() {
        return traceMetadata;
    }

    public void setTraceMetadata(Map<String, String> traceMetadata) {
        this.traceMetadata = traceMetadata;
    }
}
