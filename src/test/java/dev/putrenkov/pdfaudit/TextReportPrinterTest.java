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
}
