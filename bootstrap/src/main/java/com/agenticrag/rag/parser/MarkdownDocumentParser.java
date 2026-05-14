package com.agenticrag.rag.parser;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class MarkdownDocumentParser implements DocumentParser {

    private final Parser parser;

    public MarkdownDocumentParser() {
        this.parser = Parser.builder().build();
    }

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        try {
            String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Node document = parser.parse(markdown);
            TextExtractor extractor = new TextExtractor();
            document.accept(extractor);
            return extractor.getText();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse Markdown document", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "md".equalsIgnoreCase(fileExtension) || "markdown".equalsIgnoreCase(fileExtension);
    }

    private static class TextExtractor extends AbstractVisitor {
        private final StringBuilder sb = new StringBuilder();
        private int orderedListNumber = 0;

        @Override
        public void visit(Heading heading) {
            ensureBlockSpacing();
            sb.append("#".repeat(Math.max(1, heading.getLevel()))).append(" ");
            visitChildren(heading);
            sb.append("\n\n");
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            sb.append("\n\n");
        }

        @Override
        public void visit(BulletList bulletList) {
            visitChildren(bulletList);
            sb.append("\n");
        }

        @Override
        public void visit(OrderedList orderedList) {
            int previous = orderedListNumber;
            orderedListNumber = orderedList.getStartNumber();
            visitChildren(orderedList);
            orderedListNumber = previous;
            sb.append("\n");
        }

        @Override
        public void visit(ListItem listItem) {
            if (listItem.getParent() instanceof OrderedList) {
                sb.append(orderedListNumber++).append(". ");
            } else {
                sb.append("- ");
            }
            visitChildren(listItem);
            sb.append("\n");
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            ensureBlockSpacing();
            sb.append("```");
            if (fencedCodeBlock.getInfo() != null && !fencedCodeBlock.getInfo().isBlank()) {
                sb.append(fencedCodeBlock.getInfo().trim());
            }
            sb.append("\n").append(fencedCodeBlock.getLiteral());
            if (!fencedCodeBlock.getLiteral().endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("```\n\n");
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            ensureBlockSpacing();
            sb.append("```\n").append(indentedCodeBlock.getLiteral());
            if (!indentedCodeBlock.getLiteral().endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("```\n\n");
        }

        @Override
        public void visit(Text text) {
            sb.append(text.getLiteral());
        }

        @Override
        public void visit(Code code) {
            sb.append("`").append(code.getLiteral()).append("`");
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            sb.append("\n");
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            sb.append("\n");
        }

        private void ensureBlockSpacing() {
            if (!sb.isEmpty() && !sb.toString().endsWith("\n\n")) {
                if (sb.charAt(sb.length() - 1) != '\n') {
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        public String getText() {
            return sb.toString().trim();
        }
    }
}
