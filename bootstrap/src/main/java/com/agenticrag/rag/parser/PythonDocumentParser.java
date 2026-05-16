package com.agenticrag.rag.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
public class PythonDocumentParser implements DocumentParser {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt",
            "png", "jpg", "jpeg", "bmp", "tiff", "tif", "webp",
            "md", "markdown", "html", "htm", "txt"
    );

    private static final List<String> SUPPORTED_STRATEGIES = List.of(
            "paragraph", "smart", "paper", "manual", "table", "naive", "one"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String serviceUrl;
    private final int timeoutMs;
    private final boolean fallbackEnabled;

    public PythonDocumentParser(RestTemplate restTemplate,
                                ObjectMapper objectMapper,
                                @Value("${agenticrag.parser.python.service-url:http://localhost:8000}") String serviceUrl,
                                @Value("${agenticrag.parser.python.timeout:300000}") int timeoutMs,
                                @Value("${agenticrag.parser.python.fallback-enabled:true}") boolean fallbackEnabled) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.serviceUrl = serviceUrl;
        this.timeoutMs = timeoutMs;
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        return parseStructured(inputStream, fileExtension).asPlainText();
    }

    @Override
    public StructuredParseResult parseStructured(InputStream inputStream, String fileExtension) {
        return parseStructured(inputStream, fileExtension, "smart");
    }

    @Override
    public StructuredParseResult parseStructured(InputStream inputStream, String fileExtension, String strategy) {
        try {
            return invokePythonService(inputStream, fileExtension, strategy);
        } catch (Exception ex) {
            if (fallbackEnabled) {
                throw new DocumentParseException("Python service failed, fallback needed: " + ex.getMessage(), ex);
            }
            throw new DocumentParseException("Python service failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return SUPPORTED_EXTENSIONS.contains(fileExtension.toLowerCase());
    }

    @Override
    public boolean supports(String fileExtension, String strategy) {
        if (!supports(fileExtension)) {
            return false;
        }
        return !StringUtils.hasText(strategy) || SUPPORTED_STRATEGIES.contains(strategy.trim().toLowerCase());
    }

    @Override
    public int order() {
        return 200; // Highest priority
    }

    public boolean isHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(serviceUrl + "/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private StructuredParseResult invokePythonService(InputStream inputStream, String fileExtension, String strategy) throws IOException {
        byte[] fileBytes;
        try (inputStream) {
            fileBytes = inputStream.readAllBytes();
        }

        // Build multipart request
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return "document." + fileExtension;
            }
        });
        body.add("strategy", StringUtils.hasText(strategy) ? strategy : "smart");
        body.add("language", "zh");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    serviceUrl + "/api/v1/parse",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IOException("Python service returned " + response.getStatusCode());
            }

            return parseResponse(response.getBody());
        } catch (ResourceAccessException e) {
            throw new IOException("Cannot connect to Python service at " + serviceUrl, e);
        }
    }

    private StructuredParseResult parseResponse(String responseBody) throws IOException {
        Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<>() {});

        Boolean success = (Boolean) response.get("success");
        if (success == null || !success) {
            String errorMsg = (String) response.get("error_message");
            throw new IOException("Python service parse failed: " + errorMsg);
        }

        List<LogicalSegment> segments = objectMapper.convertValue(
                response.getOrDefault("segments", List.of()),
                new TypeReference<List<LogicalSegment>>() {}
        );

        List<PageDebugInfo> pages = objectMapper.convertValue(
                response.getOrDefault("pages", List.of()),
                new TypeReference<List<PageDebugInfo>>() {}
        );

        Map<String, Object> metadata = objectMapper.convertValue(
                response.getOrDefault("document_metadata", Map.of()),
                new TypeReference<Map<String, Object>>() {}
        );
        metadata = new LinkedHashMap<>(metadata);
        metadata.put("parserEngine", "python_service");

        return new StructuredParseResult(segments, pages, metadata);
    }
}
