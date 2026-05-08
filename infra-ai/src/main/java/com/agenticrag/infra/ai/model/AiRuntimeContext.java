package com.agenticrag.infra.ai.model;

/**
 * AI 运行时上下文，包装了配置和增强功能
 */
public class AiRuntimeContext {

    private final AiRuntimeOptions options;

    private final AiEnhancement enhancement;

    public AiRuntimeContext(AiRuntimeOptions options) {
        this(options, new AiEnhancement());
    }

    public AiRuntimeContext(AiRuntimeOptions options, AiEnhancement enhancement) {
        this.options = options;
        this.enhancement = enhancement != null ? enhancement : new AiEnhancement();
    }

    public AiRuntimeOptions getOptions() {
        return options;
    }

    public AiEnhancement getEnhancement() {
        return enhancement;
    }

    /**
     * 快捷方法：根据 Provider 类型转换 Options
     */
    @SuppressWarnings("unchecked")
    public <T extends AiRuntimeOptions> T getOptionsAs(Class<T> clazz) {
        if (clazz.isInstance(options)) {
            return (T) options;
        }
        throw new IllegalArgumentException("Options is not an instance of " + clazz.getName());
    }
}
