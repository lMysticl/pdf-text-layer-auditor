package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubWorkflowReporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void emitsEscapedAnnotationsSummaryAndOutputs() throws IOException {
        Path summary = temporaryDirectory.resolve("summary.md");
        Path outputs = temporaryDirectory.resolve("outputs.txt");
        Map<String, String> environment = baseEnvironment();
        environment.put("GITHUB_STEP_SUMMARY", summary.toString());
        environment.put("GITHUB_OUTPUT", outputs.toString());
        ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
        ActionRun run = new ActionRun(
                List.of(ActionRun.AuditedPdf.success(
                        "docs/a,b%file|x.pdf",
                        ActionTestReports.withNoTextLayer(
                                temporaryDirectory.resolve("a.pdf")))),
                true);

        new GitHubWorkflowReporter(
                environment,
                new PrintStream(logBytes, true, StandardCharsets.UTF_8))
                .publish(run, ActionOptions.fromEnvironment(environment));

        String log = logBytes.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("file=docs/a%2Cb%25file|x.pdf"));
        assertTrue(log.contains("Page 1: NO_TEXT_LAYER"));
        assertTrue(
                Files.readString(summary)
                        .contains("<code>docs/a,b%file&#124;x.pdf</code>"));
        String outputText = Files.readString(outputs);
        assertTrue(outputText.contains("files_checked=1"));
        assertTrue(outputText.contains("files_with_findings=1"));
        assertTrue(outputText.contains("files_failed=0"));
        assertTrue(outputText.contains("report_path=pdf-text-layer-audit.json"));
    }

    @Test
    void capsAnnotationsWithoutHidingReportCount() throws IOException {
        Map<String, String> environment = baseEnvironment();
        environment.put("INPUT_MAX_ANNOTATIONS", "0");
        ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
        ActionRun run = new ActionRun(
                List.of(ActionRun.AuditedPdf.success(
                        "document.pdf",
                        ActionTestReports.withNoTextLayer(
                                temporaryDirectory.resolve("document.pdf")))),
                true);

        new GitHubWorkflowReporter(
                environment,
                new PrintStream(logBytes, true, StandardCharsets.UTF_8))
                .publish(run, ActionOptions.fromEnvironment(environment));

        String log = logBytes.toString(StandardCharsets.UTF_8);
        assertEquals(0, occurrences(log, "::warning"));
        assertTrue(log.contains("1 additional annotation(s)"));
    }

    private Map<String, String> baseEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("INPUT_TOKEN", "token");
        environment.put("GITHUB_WORKSPACE", temporaryDirectory.toString());
        return environment;
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(target, from)) >= 0) {
            count++;
            from += target.length();
        }
        return count;
    }
}
