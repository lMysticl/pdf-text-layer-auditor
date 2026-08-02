package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void jsonOptionPrintsJsonAndPreservesFindingExitCode() throws IOException {
        Path pdf = temporaryDirectory.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        CliResult result = invoke("--json", pdf.toString());

        String json = result.stdout().trim();
        assertEquals(1, result.exitCode());
        assertTrue(json.startsWith("{\"schemaVersion\":6,\"file\":"));
        assertTrue(json.contains("\"pageCount\":1"));
        assertTrue(json.contains("\"code\":\"NO_TEXT_LAYER\""));
        assertTrue(json.endsWith("]}"));
        assertTrue(result.stderr().isEmpty());
    }

    @Test
    void textReportPrintsToStdoutOnly() throws IOException {
        Path pdf = createBlankPdf("blank-text-report.pdf");

        CliResult result = invoke(pdf.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("PDF Text Layer Audit"));
        assertTrue(result.stdout().contains("Result: 1 of 1 page needs attention"));
        assertTrue(result.stderr().isEmpty());
    }

    @Test
    void versionOptionPrintsDevelopmentIdentityToStdoutOnly() {
        CliResult result = invoke("--version");

        assertEquals(0, result.exitCode());
        assertEquals("pdf-text-layer-auditor development", result.stdout().trim());
        assertTrue(result.stderr().isEmpty());
    }

    @Test
    void helpOptionPrintsUsageToStdoutOnly() {
        CliResult result = invoke("--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("Usage:"));
        assertTrue(result.stdout().contains("Invalid arguments, audit failure, or output failure"));
        assertTrue(result.stderr().isEmpty());
    }

    @Test
    void invalidArgumentsPrintUsageToStderrOnly() {
        CliResult result = invoke("--unknown");

        assertEquals(2, result.exitCode());
        assertTrue(result.stdout().isEmpty());
        assertTrue(result.stderr().startsWith("Unknown option: --unknown"));
        assertTrue(result.stderr().contains("Usage:"));
    }

    @Test
    void configuredPageLimitReachesAuditor() throws IOException {
        Path pdf = temporaryDirectory.resolve("two-pages.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        CliResult result = invoke("--max-pages", "1", pdf.toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.stdout().isEmpty());
        assertTrue(result.stderr().contains("configured page limit of 1"));
    }

    @Test
    void escapesTerminalControlsInErrorMessages() {
        CliResult result = invoke("missing" + (char) 0x1B + "[31m.pdf");

        assertEquals(2, result.exitCode());
        assertTrue(result.stdout().isEmpty());
        assertTrue(result.stderr().contains("missing\\u001B[31m.pdf"));
        assertFalse(result.stderr().contains(String.valueOf((char) 0x1B)));
    }

    @Test
    void passwordProtectedPdfPrintsStableDiagnosticToStderrOnly() throws IOException {
        Path pdf = createEncryptedPdf("password.pdf", "user-password", true);

        CliResult result = invoke(pdf.toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.stdout().isEmpty());
        assertEquals(
                "Cannot audit a password-protected PDF without a password.",
                result.stderr().trim());
    }

    @Test
    void extractionRestrictedPdfPrintsStableDiagnosticToStderrOnly() throws IOException {
        Path pdf = createEncryptedPdf("restricted.pdf", "", false);

        CliResult result = invoke(pdf.toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.stdout().isEmpty());
        assertEquals(
                "PDF permissions do not allow text extraction",
                result.stderr().trim());
    }

    @Test
    void workLimitFailureHasStableMachineReadableMarker() throws IOException {
        Path pdf = temporaryDirectory.resolve("two-glyphs.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.showText("AB");
                content.endText();
            }
            document.save(pdf.toFile());
        }
        AuditWorkLimits defaults = AuditWorkLimits.defaults();
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream output = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8);
                PrintStream error = new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            exitCode = Main.run(
                    new String[] {pdf.toString()},
                    output,
                    error,
                    new AuditWorkLimits(
                            1,
                            defaults.maximumSemanticCharacterCount(),
                            defaults.maximumFontCount(),
                            defaults.maximumImageCount(),
                            defaults.maximumPaintedVectorPathCount(),
                            defaults.maximumAnnotationCount(),
                            defaults.maximumAnnotationAppearanceStreamCount(),
                            defaults.maximumOptionalContentReferenceCount()));
        }

        assertEquals(2, exitCode);
        assertTrue(capturedOutput.toString(StandardCharsets.UTF_8).isEmpty());
        assertEquals(
                "pdfTextLayerAuditorFailure=WORK_LIMIT_GLYPH_COUNT"
                        + System.lineSeparator()
                        + "PDF exceeds the configured glyph-count limit of 1",
                capturedError.toString(StandardCharsets.UTF_8).trim());
    }

    @Test
    void jvmResourceFailuresHaveStableMachineReadableMarkers() {
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        try (PrintStream error =
                new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            assertEquals(2, Main.resourceLimit(error, "HEAP"));
            assertEquals(2, Main.resourceLimit(error, "STACK"));
        }

        assertEquals(
                "pdfTextLayerAuditorFailure=WORK_LIMIT_HEAP"
                        + System.lineSeparator()
                        + "pdfTextLayerAuditorFailure=WORK_LIMIT_STACK",
                capturedError.toString(StandardCharsets.UTF_8).trim());
    }

    @Test
    void textReportOutputFailureReturnsExitCodeTwoAndUsesStderr() throws IOException {
        Path pdf = createBlankPdf("failed-text-output.pdf");
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream failingOutput = new PrintStream(
                        new OutputStream() {
                            @Override
                            public void write(int value) throws IOException {
                                throw new IOException("output closed");
                            }
                        },
                        true,
                        StandardCharsets.UTF_8);
                PrintStream error =
                        new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            exitCode = Main.run(new String[] {pdf.toString()}, failingOutput, error);
        }

        assertEquals(2, exitCode);
        assertEquals(
                "Could not write command output.",
                capturedError.toString(StandardCharsets.UTF_8).trim());
    }

    private CliResult invoke(String... arguments) {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        try (PrintStream output =
                        new PrintStream(capturedOutput, true, StandardCharsets.UTF_8);
                PrintStream error =
                        new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            int exitCode = Main.run(arguments, output, error);
            return new CliResult(
                    exitCode,
                    capturedOutput.toString(StandardCharsets.UTF_8),
                    capturedError.toString(StandardCharsets.UTF_8));
        }
    }

    private Path createBlankPdf(String fileName) throws IOException {
        Path pdf = temporaryDirectory.resolve(fileName);
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private Path createEncryptedPdf(
            String fileName,
            String userPassword,
            boolean extractionAllowed
    ) throws IOException {
        Path pdf = temporaryDirectory.resolve(fileName);
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            AccessPermission permissions = new AccessPermission();
            permissions.setCanExtractContent(extractionAllowed);
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-password", userPassword, permissions);
            policy.setEncryptionKeyLength(128);
            policy.setPreferAES(true);
            document.protect(policy);
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private record CliResult(int exitCode, String stdout, String stderr) {
    }
}
