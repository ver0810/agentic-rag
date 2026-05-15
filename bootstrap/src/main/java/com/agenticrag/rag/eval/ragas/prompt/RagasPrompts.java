package com.agenticrag.rag.eval.ragas.prompt;

public final class RagasPrompts {

    private RagasPrompts() {}

    public static final String FAITHFULNESS_PROMPT = """
            你是一个忠实度评估专家。请评估给定的答案是否完全基于提供的上下文，不包含任何幻觉或编造的信息。

            评估规则：
            1. 答案中的每个声明都必须能在上下文中找到依据
            2. 如果答案包含上下文中没有的信息，视为不忠实
            3. 答案可以进行合理的总结和推理，但不能添加新事实

            上下文：
            %s

            问题：%s

            答案：%s

            请只输出 JSON，包含以下字段：
            - "isFaithful": 布尔值，true 表示忠实，false 表示不忠实
            - "reason": 字符串，简要说明理由
            - "score": 浮点数，0.0-1.0 之间的忠实度评分

            输出示例：
            {"isFaithful": true, "reason": "所有声明均有上下文支持", "score": 1.0}
            """;

    public static final String CONTEXT_RECALL_PROMPT = """
            你是一个上下文召回率评估专家。请评估检索到的上下文是否包含了标准答案中的所有关键信息。

            评估规则：
            1. 将标准答案分解为独立的声明/事实
            2. 检查每个声明是否在上下文中有所体现
            3. 计算被上下文覆盖的声明比例

            检索到的上下文：
            %s

            问题：%s

            标准答案：%s

            请只输出 JSON，包含以下字段：
            - "coveredPoints": 字符串数组，列出被上下文覆盖的要点
            - "uncoveredPoints": 字符串数组，列出未被覆盖的要点
            - "score": 浮点数，0.0-1.0 之间的召回率评分

            输出示例：
            {"coveredPoints": ["要点1", "要点2"], "uncoveredPoints": [], "score": 1.0}
            """;

    public static final String CONTEXT_PRECISION_PROMPT = """
            你是一个上下文精确度评估专家。请评估检索到的文档片段与问题的相关性，以及排序是否合理。

            评估规则：
            1. 评估每个文档片段与问题的相关性
            2. 相关文档应该排在前面
            3. 计算加权精确度（相关文档的排名分数）

            文档片段（按检索顺序）：
            %s

            问题：%s

            标准答案：%s

            请只输出 JSON，包含以下字段：
            - "relevantChunkIds": 整数数组，列出相关的片段编号
            - "irrelevantChunkIds": 整数数组，列出不相关的片段编号
            - "score": 浮点数，0.0-1.0 之间的精确度评分

            输出示例：
            {"relevantChunkIds": [1, 2, 4], "irrelevantChunkIds": [3, 5], "score": 0.8}
            """;

    public static final String ANSWER_RELEVANCY_PROMPT = """
            你是一个答案相关性评估专家。请评估生成的答案是否与问题相关，是否回答了问题。

            评估规则：
            1. 答案应该直接回答问题
            2. 答案不应该包含无关信息
            3. 答案应该完整，不应该遗漏关键部分

            问题：%s

            生成的答案：%s

            请只输出 JSON，包含以下字段：
            - "answered": 布尔值或字符串，true 表示已回答，false 表示未回答，"partial" 表示部分回答
            - "evaluation": 字符串，相关性简要评价
            - "score": 浮点数，0.0-1.0 之间的相关性评分

            输出示例：
            {"answered": true, "evaluation": "答案直接回应了问题", "score": 1.0}
            """;

    public static final String ANSWER_CORRECTNESS_PROMPT = """
            你是一个答案正确性评估专家。请评估生成的答案与标准答案的匹配程度。

            评估规则：
            1. 比较关键事实是否一致
            2. 允许表达方式不同，但核心信息必须匹配
            3. 考虑语义相似性而非字面匹配

            问题：%s

            生成的答案：%s

            标准答案：%s

            请只输出 JSON，包含以下字段：
            - "consistentFacts": 字符串数组，列出一致的要点
            - "inconsistentFacts": 字符串数组，列出不一致的要点
            - "missingFacts": 字符串数组，列出遗漏的要点
            - "score": 浮点数，0.0-1.0 之间的正确性评分

            输出示例：
            {"consistentFacts": ["事实1"], "inconsistentFacts": [], "missingFacts": ["事实2"], "score": 0.8}
            """;
}
