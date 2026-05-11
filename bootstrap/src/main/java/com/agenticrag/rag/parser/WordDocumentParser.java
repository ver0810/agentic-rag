package com.agenticrag.rag.parser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileExtension) {
        if ("docx".equalsIgnoreCase(fileExtension)) {
            return parseDocx(inputStream);
        } else if ("doc".equalsIgnoreCase(fileExtension)) {
            return parseDoc(inputStream);
        }
        throw new DocumentParseException("Unsupported Word format: " + fileExtension);
    }

    private String parseDocx(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();
            
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    content.append(text).append("\n");
                }
            }
            
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        content.append(cell.getText()).append("\t");
                    }
                    content.append("\n");
                }
            }
            
            return content.toString();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse DOCX document", e);
        }
    }

    private String parseDoc(InputStream inputStream) {
        try (HWPFDocument document = new HWPFDocument(inputStream)) {
            return document.getDocumentText();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse DOC document", e);
        }
    }

    @Override
    public boolean supports(String fileExtension) {
        return "docx".equalsIgnoreCase(fileExtension) || "doc".equalsIgnoreCase(fileExtension);
    }
}
