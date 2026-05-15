package com.agenticrag.rag.parser;

import com.agenticrag.infra.ai.config.AiObservabilityProperties;
import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.knowledge.service.ChunkResult;
import com.agenticrag.knowledge.service.DocumentChunkingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserStructureTests {

    @Test
    void markdownParserShouldProduceStructuredSegments() {
        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        String markdown = """
                # Product Guide

                Intro paragraph.

                ## Setup

                - Step one
                - Step two
                """;

        StructuredParseResult result = parser.parseStructured(
                new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)), "md");

        assertEquals(4, result.segments().size());
        assertEquals("heading", result.segments().get(0).type());
        assertEquals("Product Guide", result.segments().get(1).headingPath());
        assertEquals("Product Guide > Setup", result.segments().get(3).headingPath());
        assertEquals("list", result.segments().get(3).type());
    }

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

    @Test
    void chunkingShouldUseStructuredSegmentsAsBoundary() {
        DocumentChunkingService service = new DocumentChunkingService(new ObjectMapper(), tokenCostEstimator());
        StructuredParseResult parseResult = new StructuredParseResult(List.of(
                new LogicalSegment("seg-0", "heading", "# Guide", "Guide", 0, 0, java.util.Map.of("headingLevel", 1, "segmentType", "heading")),
                new LogicalSegment("seg-1", "paragraph", "Intro content for the guide.", "Guide", 1, 1, java.util.Map.of("segmentType", "paragraph")),
                new LogicalSegment("seg-2", "heading", "## Setup", "Guide > Setup", 2, 2, java.util.Map.of("headingLevel", 2, "segmentType", "heading")),
                new LogicalSegment("seg-3", "table", "| Name | Value |\n| --- | --- |\n| Mode | Fast |", "Guide > Setup", 3, 3, java.util.Map.of("segmentType", "table", "tableMarkdown", "| Name | Value |"))
        ), java.util.Map.of("docType", "md"));

        List<ChunkResult> chunks = service.chunkWithMetadata(parseResult, "paragraph", "{\"maxChars\":120,\"minChunkChars\":20}");

        assertEquals(2, chunks.size());
        assertEquals("Guide", chunks.get(0).headingPath());
        assertEquals("Guide > Setup", chunks.get(1).headingPath());
        assertEquals("table", chunks.get(1).metadata().get("segmentType"));
    }

    @Test
    void parserFactoryShouldAcceptExtensionAndStrategyRouting() {
        MarkdownDocumentParser markdownParser = new MarkdownDocumentParser();
        PythonDocumentParser pythonParser = Mockito.mock(PythonDocumentParser.class);
        Mockito.when(pythonParser.isHealthy()).thenReturn(false);
        DocumentParserFactory factory = new DocumentParserFactory(List.of(markdownParser), pythonParser);

        DocumentParser parser = factory.getParser("md", "manual");

        assertSame(markdownParser, parser);
    }

    @Test
    void chunkingShouldKeepManualSectionsSeparated() {
        DocumentChunkingService service = new DocumentChunkingService(new ObjectMapper(), tokenCostEstimator());
        StructuredParseResult parseResult = new StructuredParseResult(List.of(
                new LogicalSegment("seg-0", "heading", "# Guide", "Guide", 0, 0, java.util.Map.of("headingLevel", 1, "segmentType", "heading")),
                new LogicalSegment("seg-1", "paragraph", "Install prerequisites before setup.", "Guide > Install", 1, 1, java.util.Map.of("segmentType", "paragraph")),
                new LogicalSegment("seg-2", "paragraph", "Run setup command and verify output.", "Guide > Setup", 2, 2, java.util.Map.of("segmentType", "paragraph"))
        ), java.util.Map.of("docType", "md"));

        List<ChunkResult> chunks = service.chunkWithMetadata(parseResult, "manual", "{\"maxChars\":200,\"minChunkChars\":10}");

        assertEquals(2, chunks.size());
        assertEquals("Guide > Install", chunks.get(0).headingPath());
        assertEquals("Guide > Setup", chunks.get(1).headingPath());
        assertEquals("manual", chunks.get(0).metadata().get("chunkStrategy"));
    }

    @Test
    void chunkingShouldIsolateTablesForTableStrategy() {
        DocumentChunkingService service = new DocumentChunkingService(new ObjectMapper(), tokenCostEstimator());
        StructuredParseResult parseResult = new StructuredParseResult(List.of(
                new LogicalSegment("seg-0", "paragraph", "Monthly metrics summary.", "Metrics", 0, 0, java.util.Map.of("segmentType", "paragraph")),
                new LogicalSegment("seg-1", "table", "| Name | Value |\n| --- | --- |\n| Latency | 20ms |", "Metrics", 1, 1, java.util.Map.of("segmentType", "table", "tableMarkdown", "| Name | Value |")),
                new LogicalSegment("seg-2", "caption", "Table 1: Runtime metrics", "Metrics", 2, 2, java.util.Map.of("segmentType", "caption"))
        ), java.util.Map.of("docType", "md"));

        List<ChunkResult> chunks = service.chunkWithMetadata(parseResult, "table", "{\"maxChars\":200,\"minChunkChars\":10}");

        assertEquals(3, chunks.size());
        assertEquals("paragraph", chunks.get(0).metadata().get("segmentType"));
        assertEquals("table", chunks.get(1).metadata().get("segmentType"));
        assertEquals("caption", chunks.get(2).metadata().get("segmentType"));
        assertEquals("table", chunks.get(1).metadata().get("chunkStrategy"));
    }

    private TokenCostEstimator tokenCostEstimator() {
        AiObservabilityProperties properties = new AiObservabilityProperties();
        return new TokenCostEstimator(properties);
    }
}
