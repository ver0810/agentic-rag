package com.agenticrag.rag.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfDocumentParserTests {

    @Test
    void pdfParserShouldPreserveParagraphAndPageSpacing() throws IOException {
        PdfDocumentParser parser = new PdfDocumentParser();
        byte[] pdfBytes = createSamplePdf();

        String parsed = parser.parse(new ByteArrayInputStream(pdfBytes), "pdf");

        assertTrue(parsed.contains("# Guide Title"));
        assertTrue(parsed.contains("First paragraph of the document."));
        assertTrue(parsed.contains("Second page content."));
        assertTrue(parsed.contains("\n\n"));
    }

    private byte[] createSamplePdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage firstPage = new PDPage();
            PDPage secondPage = new PDPage();
            document.addPage(firstPage);
            document.addPage(secondPage);

            try (PDPageContentStream stream = new PDPageContentStream(document, firstPage)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                stream.newLineAtOffset(72, 720);
                stream.showText("Guide Title");
                stream.newLineAtOffset(0, -28);
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.showText("First paragraph of the document.");
                stream.newLineAtOffset(0, -36);
                stream.showText("Another paragraph follows here.");
                stream.endText();
            }

            try (PDPageContentStream stream = new PDPageContentStream(document, secondPage)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Second page content.");
                stream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
