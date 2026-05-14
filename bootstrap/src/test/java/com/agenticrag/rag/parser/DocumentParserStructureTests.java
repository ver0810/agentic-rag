package com.agenticrag.rag.parser;

import com.agenticrag.infra.ai.config.AiObservabilityProperties;
import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.knowledge.service.ChunkResult;
import com.agenticrag.knowledge.service.DocumentChunkingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserStructureTests {

    @Test
    void markdownParserShouldPreserveHeadingAndCodeBlockStructure() {
        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        String markdown = """
                # Title

                Intro paragraph.

                ## Details

                ```java
                System.out.println("hi");
                ```
                """;

        String parsed = parser.parse(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)), "md");

        assertTrue(parsed.contains("# Title"));
        assertTrue(parsed.contains("## Details"));
        assertTrue(parsed.contains("```java"));
        assertTrue(parsed.contains("System.out.println(\"hi\");"));
    }

    @Test
    void htmlParserShouldConvertHeadingsAndTablesToStructuredText() {
        HtmlDocumentParser parser = new HtmlDocumentParser();
        String html = """
                <html><body>
                <h1>Guide</h1>
                <p>Overview</p>
                <table>
                  <tr><th>Name</th><th>Value</th></tr>
                  <tr><td>Mode</td><td>Fast</td></tr>
                </table>
                </body></html>
                """;

        String parsed = parser.parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), "html");

        assertTrue(parsed.contains("# Guide"));
        assertTrue(parsed.contains("Overview"));
        assertTrue(parsed.contains("| Name | Value |"));
        assertTrue(parsed.contains("| Mode | Fast |"));
    }

    @Test
    void chunkingShouldRetainHeadingPathFromStructuredMarkdown() {
        DocumentChunkingService service = new DocumentChunkingService(new ObjectMapper(), tokenCostEstimator());
        String content = """
                # Product Guide

                This is the introduction paragraph for the guide with additional details that make the first section long enough.

                ## Setup

                Setup details are written here with enough content to form a second chunk and validate heading propagation.
                """;

        List<ChunkResult> chunks = service.chunkWithMetadata(content, "paragraph", "{\"maxChars\":80,\"minChunkChars\":40}");

        assertEquals(2, chunks.size());
        assertEquals("# Product Guide", chunks.get(0).headingPath());
        assertEquals("## Setup", chunks.get(1).headingPath());
        assertEquals(1, chunks.get(0).metadata().get("headingLevel"));
        assertEquals(2, chunks.get(1).metadata().get("headingLevel"));
        assertEquals("paragraph", chunks.get(0).metadata().get("segmentType"));
    }

    @Test
    void chunkingShouldInferCodeAndTableMetadata() {
        DocumentChunkingService service = new DocumentChunkingService(new ObjectMapper(), tokenCostEstimator());

        List<ChunkResult> codeChunks = service.chunkWithMetadata("""
                # API

                ```java
                System.out.println("hello");
                ```
                """, "paragraph", "{\"maxChars\":200}");

        List<ChunkResult> tableChunks = service.chunkWithMetadata("""
                # Metrics

                | Name | Value |
                | --- | --- |
                | Latency | 20ms |
                """, "paragraph", "{\"maxChars\":200}");

        assertEquals("code", codeChunks.get(0).metadata().get("segmentType"));
        assertEquals(Boolean.TRUE, codeChunks.get(0).metadata().get("hasCodeBlock"));
        assertEquals("table", tableChunks.get(0).metadata().get("segmentType"));
        assertEquals(Boolean.TRUE, tableChunks.get(0).metadata().get("hasTable"));
    }

    private TokenCostEstimator tokenCostEstimator() {
        AiObservabilityProperties properties = new AiObservabilityProperties();
        return new TokenCostEstimator(properties);
    }
}
