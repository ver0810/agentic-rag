package com.agenticrag.rag.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TesseractCliDocumentOcrServiceTests {

    @Test
    void parseTsvShouldMergeWordsIntoLineBlocks() {
        String tsv = String.join("\n",
                "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext",
                "5\t1\t1\t1\t1\t1\t10\t20\t30\t10\t95\tScanned",
                "5\t1\t1\t1\t1\t2\t45\t20\t25\t10\t90\tTitle",
                "5\t1\t1\t1\t2\t1\t10\t45\t40\t10\t88\tSecond",
                "5\t1\t1\t1\t2\t2\t55\t45\t50\t10\t86\tline");

        List<OcrTextBlock> blocks = TesseractCliDocumentOcrService.parseTsv(tsv, 200, 100);

        assertEquals(2, blocks.size());
        assertEquals("Scanned Title", blocks.get(0).text());
        assertEquals("Second line", blocks.get(1).text());
        assertTrue(blocks.get(0).bbox().get("x1") < blocks.get(0).bbox().get("x2"));
        assertTrue(blocks.get(0).bbox().get("y1") < blocks.get(0).bbox().get("y2"));
        assertTrue(blocks.get(0).confidence() > 0.9d);
    }
}
