package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfTextLayerAuditorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void distinguishesTextPageFromBlankPage() throws IOException {
        Path pdf = temporaryDirectory.resolve("mixed.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage textPage = new PDPage();
            document.addPage(textPage);
            try (PDPageContentStream content = new PDPageContentStream(document, textPage)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Searchable text");
                content.endText();
            }

            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);

        assertEquals(2, report.pageCount());
        assertEquals(2, report.pages().size());
        assertFalse(report.pages().get(0).needsAttention());
        assertTrue(report.pages().get(0).glyphCount() > 0);
        assertEquals(
                java.util.List.of(Finding.NO_TEXT_LAYER),
                report.pages().get(1).findings());
    }

    @Test
    void rejectsFilesAboveConfiguredLimit() throws IOException {
        Path file = temporaryDirectory.resolve("oversized.pdf");
        java.nio.file.Files.writeString(file, "not really a PDF");

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PdfTextLayerAuditor(2, 10).audit(file));

        assertTrue(exception.getMessage().contains("size limit"));
    }

    @Test
    void rejectsDocumentWithoutPages() throws IOException {
        Path pdf = temporaryDirectory.resolve("empty.pdf");
        try (PDDocument document = new PDDocument()) {
            document.save(pdf.toFile());
        }

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PdfTextLayerAuditor().audit(pdf));

        assertEquals("PDF contains no pages", exception.getMessage());
    }

    @Test
    void reportsUnnamedFontWithoutCrashing() throws IOException {
        Path pdf = temporaryDirectory.resolve("unnamed-font.pdf");
        try (PDDocument document = new PDDocument()) {
            addTwoFontPage(document, createType3Font(document, null));
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);

        assertEquals(
                List.of("<unnamed>", "Helvetica"),
                report.pages().getFirst().fonts().stream().map(FontAudit::name).toList());
    }

    @Test
    void keepsDifferentFontStatesWithSameNameSeparate() throws IOException {
        Path pdf = temporaryDirectory.resolve("same-name-fonts.pdf");
        try (PDDocument document = new PDDocument()) {
            addTwoFontPage(document, createType3Font(document, "Helvetica"));
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        List<FontAudit> fonts = report.pages().getFirst().fonts();

        assertEquals(2, fonts.size());
        assertEquals(List.of("Helvetica", "Helvetica"), fonts.stream().map(FontAudit::name).toList());
        assertEquals(List.of(false, true), fonts.stream().map(FontAudit::embedded).toList());
    }

    private static void addTwoFontPage(PDDocument document, PDType3Font type3Font)
            throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        page.setResources(new PDResources());
        page.getResources().put(COSName.getPDFName("F1"), type3Font);
        page.getResources().put(
                COSName.getPDFName("F2"),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA));

        PDStream pageContent = new PDStream(document);
        try (var output = pageContent.createOutputStream()) {
            output.write(
                    ("BT /F1 12 Tf 72 720 Td <41> Tj ET "
                                    + "BT /F2 12 Tf 90 720 Td (B) Tj ET")
                            .getBytes(StandardCharsets.US_ASCII));
        }
        page.setContents(pageContent);
    }

    private static PDType3Font createType3Font(PDDocument document, String name)
            throws IOException {
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.TYPE, COSName.FONT);
        dictionary.setItem(COSName.SUBTYPE, COSName.TYPE3);
        if (name != null) {
            dictionary.setItem(COSName.NAME, COSName.getPDFName(name));
        }
        dictionary.setItem(
                COSName.FONT_BBOX,
                new PDRectangle(0, 0, 600, 700).getCOSArray());
        dictionary.setItem(
                COSName.FONT_MATRIX,
                new Matrix(0.001f, 0, 0, 0.001f, 0, 0).toCOSArray());
        dictionary.setInt(COSName.FIRST_CHAR, 65);
        dictionary.setInt(COSName.LAST_CHAR, 65);
        COSArray widths = new COSArray();
        widths.add(new COSFloat(600));
        dictionary.setItem(COSName.WIDTHS, widths);
        dictionary.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING);

        COSDictionary characterProcedures = new COSDictionary();
        COSStream characterStream = document.getDocument().createCOSStream();
        try (var output = characterStream.createOutputStream()) {
            output.write(
                    "0 0 600 700 d1 0 0 600 700 re f"
                            .getBytes(StandardCharsets.US_ASCII));
        }
        characterProcedures.setItem(COSName.getPDFName("A"), characterStream);
        dictionary.setItem(COSName.CHAR_PROCS, characterProcedures);
        return new PDType3Font(dictionary);
    }
}
