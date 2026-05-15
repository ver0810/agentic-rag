package com.agenticrag.infrastructure.reranker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashScopeRerankerAdapterTests {

    @Test
    void shouldUseCorrectDashScopeDefaultUrl() throws Exception {
        DashScopeRerankerAdapter adapter = new DashScopeRerankerAdapter(
                null,
                "test-key",
                "qwen3-rerank",
                new ObjectMapper());

        assertEquals(
                "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank",
                readApiUrl(adapter));
    }

    @Test
    void shouldNormalizeLegacyDashScopeUrl() throws Exception {
        DashScopeRerankerAdapter adapter = new DashScopeRerankerAdapter(
                "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-reranking/text-reranking",
                "test-key",
                "qwen3-rerank",
                new ObjectMapper());

        assertEquals(
                "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank",
                readApiUrl(adapter));
    }

    private String readApiUrl(DashScopeRerankerAdapter adapter) throws Exception {
        Field field = DashScopeRerankerAdapter.class.getDeclaredField("apiUrl");
        field.setAccessible(true);
        return (String) field.get(adapter);
    }
}
