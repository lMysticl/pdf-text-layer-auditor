package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

class MainTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void jsonOptionPrintsJsonAndPreservesFindingExitCode() throws IOException {
        Path pdf = temporaryDirectory.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream replacement =
                new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            System.setOut(replacement);
            exitCode = Main.run(new String[] {"--json", pdf.toString()});
        } finally {
            System.setOut(originalOut);
        }

        String json = capturedOutput.toString(StandardCharsets.UTF_8).trim();
        assertEquals(1, exitCode);
        assertTrue(json.startsWith("{\"schemaVersion\":1,\"file\":"));
        assertTrue(json.contains("\"pageCount\":1"));
        assertTrue(json.contains("\"code\":\"NO_TEXT_LAYER\""));
        assertTrue(json.endsWith("]}"));
    }

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void versionOptionPrintsDevelopmentIdentityInTests() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream replacement =
                new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            System.setOut(replacement);
            exitCode = Main.run(new String[] {"--version"});
        } finally {
            System.setOut(originalOut);
        }

        assertEquals(0, exitCode);
        assertEquals(
                "pdf-text-layer-auditor development",
                capturedOutput.toString(StandardCharsets.UTF_8).trim());
    }

    @Test
    @ResourceLock(Resources.SYSTEM_ERR)
    void configuredPageLimitReachesAuditor() throws IOException {
        Path pdf = temporaryDirectory.resolve("two-pages.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream replacement =
                new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            System.setErr(replacement);
            exitCode = Main.run(new String[] {
                "--max-pages", "1", pdf.toString()
            });
        } finally {
            System.setErr(originalErr);
        }

        assertEquals(2, exitCode);
        assertTrue(capturedError.toString(StandardCharsets.UTF_8)
                .contains("configured page limit of 1"));
    }
}
