package com.agenticrag.infrastructure.reranker;

import com.agenticrag.infra.ai.config.RagProperties;
import com.agenticrag.infra.ai.port.reranker.RerankerPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RerankerConfiguration {

    @Bean
    @ConditionalOnProperty(name = "agenticrag.ai.rag.reranker-type", havingValue = "lexical", matchIfMissing = true)
    public RerankerPort lexicalReranker(RagProperties ragProperties) {
        return new LexicalRerankerAdapter(
                (float) ragProperties.getRerankerRetrievalWeight(),
                (float) ragProperties.getRerankerLexicalWeight());
    }

    @Bean
    @ConditionalOnProperty(name = "agenticrag.ai.rag.reranker-type", havingValue = "cross-encoder")
    public RerankerPort crossEncoderReranker(RagProperties ragProperties, ObjectMapper objectMapper) {
        return new CrossEncoderRerankerAdapter(
                ragProperties.getRerankerApiUrl(),
                ragProperties.getRerankerApiKey(),
                ragProperties.getRerankerModel(),
                objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "agenticrag.ai.rag.reranker-type", havingValue = "dashscope")
    public RerankerPort dashscopeReranker(RagProperties ragProperties, ObjectMapper objectMapper) {
        return new DashScopeRerankerAdapter(
                ragProperties.getRerankerApiUrl(),
                ragProperties.getRerankerApiKey(),
                ragProperties.getRerankerModel(),
                objectMapper);
    }
}
