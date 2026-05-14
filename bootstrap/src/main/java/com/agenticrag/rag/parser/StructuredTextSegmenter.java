package com.agenticrag.rag.parser;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StructuredTextSegmenter {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("(?m)^\\|.+\\|\\s*$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("(?m)^(-|\\*|\\d+[.])\\s+.+$");

    private StructuredTextSegmenter() {
    }

    static StructuredParseResult segment(String content, String docType) {
        if (!StringUtils.hasText(content)) {
            return new StructuredParseResult(List.of(), Map.of("docType", docType));
        }
        String[] blocks = content.replace("\r\n", "\n").split("\\n\\s*\\n+");
        List<LogicalSegment> segments = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        int order = 0;
        for (String rawBlock : blocks) {
            String block = rawBlock == null ? "" : rawBlock.trim();
            if (!StringUtils.hasText(block)) {
                continue;
            }

            Matcher headingMatcher = MARKDOWN_HEADING_PATTERN.matcher(block);
            if (headingMatcher.matches()) {
                int level = headingMatcher.group(1).length();
                String title = headingMatcher.group(2).trim();
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(title);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("headingLevel", level);
                metadata.put("segmentType", "heading");
                metadata.put("docType", docType);
                String headingPath = String.join(" > ", headingStack);
                segments.add(new LogicalSegment(
                        "seg-" + order,
                        "heading",
                        block,
                        headingPath,
                        order,
                        order,
                        metadata));
                order++;
                continue;
            }

            String segmentType = inferSegmentType(block);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("segmentType", segmentType);
            metadata.put("docType", docType);
            if ("table".equals(segmentType)) {
                metadata.put("tableMarkdown", block);
            }
            String headingPath = headingStack.isEmpty() ? null : String.join(" > ", headingStack);
            appendOrMergeSegment(segments, new LogicalSegment(
                    "seg-" + order,
                    segmentType,
                    block,
                    headingPath,
                    order,
                    order,
                    metadata));
            order++;
        }
        return new StructuredParseResult(segments, Map.of("docType", docType, "segmentCount", segments.size()));
    }

    private static void appendOrMergeSegment(List<LogicalSegment> segments, LogicalSegment next) {
        if (segments.isEmpty()) {
            segments.add(next);
            return;
        }
        LogicalSegment previous = segments.get(segments.size() - 1);
        if ("list".equals(previous.type())
                && "list".equals(next.type())
                && java.util.Objects.equals(previous.headingPath(), next.headingPath())) {
            Map<String, Object> mergedMetadata = new LinkedHashMap<>(previous.metadata());
            mergedMetadata.putAll(next.metadata());
            segments.set(segments.size() - 1, new LogicalSegment(
                    previous.id(),
                    previous.type(),
                    previous.content() + "\n" + next.content(),
                    previous.headingPath(),
                    previous.startOrder(),
                    next.endOrder(),
                    mergedMetadata));
            return;
        }
        segments.add(next);
    }

    private static String inferSegmentType(String block) {
        if (block.startsWith("```") && block.endsWith("```")) {
            return "code";
        }
        if (TABLE_ROW_PATTERN.matcher(block).find()) {
            return "table";
        }
        if (LIST_ITEM_PATTERN.matcher(block).find()) {
            return "list";
        }
        return "paragraph";
    }
}
