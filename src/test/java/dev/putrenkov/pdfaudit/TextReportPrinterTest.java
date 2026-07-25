package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextReportPrinterTest {
    @Test
    void usesSingularFontLabelOnlyForOneFont() {
        FontAudit firstFont = new FontAudit("First", false, false, 1);
        FontAudit secondFont = new FontAudit("Second", false, false, 1);
        List<PageAudit> pages = List.of(
                new PageAudit(1, 0, 0, 0, 0, 0, List.of(), List.of()),
                new PageAudit(2, 1, 1, 0, 0, 0, List.of(firstFont), List.of()),
                new PageAudit(
                        3,
                        2,
                        2,
                        0,
                        0,
                        0,
                        List.of(firstFont, secondFont),
                        List.of()));
        AuditReport report = new AuditReport(
                Path.of("document.pdf"),
                100,
                3,
                false,
                true,
                3.0f,
                pages);

        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        try (PrintStream output =
                new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            new TextReportPrinter().print(report, output);
        }

        List<String> lines =
                capturedOutput.toString(StandardCharsets.UTF_8).lines().toList();
        assertTrue(lines.contains("Page 1: 0 glyphs, 0 Unicode characters, 0 fonts"));
        assertTrue(lines.contains("Page 2: 1 glyphs, 1 Unicode characters, 1 font"));
        assertTrue(lines.contains("Page 3: 2 glyphs, 2 Unicode characters, 2 fonts"));
    }

    @Test
    void usesSingularSummaryOnlyForOneInspectedPage() {
        PageAudit attentionPage = new PageAudit(
                1,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(Finding.NO_TEXT_LAYER));
        PageAudit healthyPage = new PageAudit(
                2,
                1,
                1,
                0,
                0,
                0,
                List.of(),
                List.of());

        assertTrue(printSummary(List.of(attentionPage))
                .contains("Result: 1 of 1 page needs attention"));
        assertTrue(printSummary(List.of(attentionPage, healthyPage))
                .contains("Result: 1 of 2 pages need attention"));
        assertTrue(printSummary(List.of(attentionPage, attentionPage))
                .contains("Result: 2 of 2 pages need attention"));
    }

    @Test
    void escapesTerminalControlsInFontNames() {
        FontAudit font = new FontAudit(
                "Bad" + (char) 0x1B + "[31mRED" + (char) 0x0A + (char) 0x202E + "txt",
                false,
                false,
                1);
        PageAudit page = new PageAudit(
                1,
                1,
                1,
                0,
                0,
                0,
                List.of(font),
                List.of());
        AuditReport report = new AuditReport(
                Path.of("document.pdf"),
                100,
                1,
                false,
                true,
                3.0f,
                List.of(page));

        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        try (PrintStream output =
                new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            new TextReportPrinter().print(report, output);
        }

        String text = capturedOutput.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Font: Bad\\u001B[31mRED\\u000A\\u202Etxt"));
        assertFalse(text.contains(String.valueOf((char) 0x1B)));
        assertFalse(text.contains(String.valueOf((char) 0x202E)));
    }

    private String printSummary(List<PageAudit> pages) {
        AuditReport report = new AuditReport(
                Path.of("document.pdf"),
                100,
                pages.size(),
                false,
                true,
                3.0f,
                pages);
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        try (PrintStream output =
                new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            new TextReportPrinter().print(report, output);
        }
        return capturedOutput.toString(StandardCharsets.UTF_8);
    }
}
