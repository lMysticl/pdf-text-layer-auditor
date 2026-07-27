package dev.putrenkov.pdfaudit.github;

import dev.putrenkov.pdfaudit.Finding;
import dev.putrenkov.pdfaudit.PageAudit;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class GitHubWorkflowReporter {
    private final Map<String, String> environment;
    private final PrintStream log;

    GitHubWorkflowReporter(Map<String, String> environment, PrintStream log) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.log = Objects.requireNonNull(log, "log");
    }

    void publish(ActionRun run, ActionOptions options) throws IOException {
        emitAnnotations(run, options.maxAnnotations());
        appendStepSummary(run);
        writeOutputs(run, environmentRelativeReportPath());
        log.printf(
                "Audited %d changed PDF file(s): %d with findings, %d failed.%n",
                run.filesChecked(),
                run.filesWithFindings(),
                run.filesFailed());
    }

    private void emitAnnotations(ActionRun run, int maximum) {
        List<Annotation> annotations = annotations(run);
        int emitted = Math.min(maximum, annotations.size());
        for (int index = 0; index < emitted; index++) {
            Annotation annotation = annotations.get(index);
            log.printf(
                    "::%s file=%s,title=%s::%s%n",
                    annotation.level(),
                    escapeProperty(annotation.file()),
                    escapeProperty(annotation.title()),
                    escapeData(annotation.message()));
        }
        if (annotations.size() > emitted) {
            log.printf(
                    "::notice title=PDF audit annotations limited::%d additional annotation(s) are available in the JSON report.%n",
                    annotations.size() - emitted);
        }
    }

    private static List<Annotation> annotations(ActionRun run) {
        List<Annotation> annotations = new ArrayList<>();
        for (ActionRun.AuditedPdf file : run.files()) {
            if (!file.succeeded()) {
                annotations.add(new Annotation(
                        "error",
                        file.repositoryPath(),
                        "PDF audit failed",
                        file.error()));
                continue;
            }
            for (PageAudit page : file.report().pages()) {
                if (!page.needsAttention()) {
                    continue;
                }
                String findings = page.findings().stream()
                        .map(Finding::name)
                        .collect(Collectors.joining(", "));
                annotations.add(new Annotation(
                        "warning",
                        file.repositoryPath(),
                        "PDF text-layer finding",
                        "Page " + page.pageNumber() + ": " + findings));
            }
        }
        return List.copyOf(annotations);
    }

    private void appendStepSummary(ActionRun run) throws IOException {
        String value = environment.get("GITHUB_STEP_SUMMARY");
        if (value == null || value.isBlank()) {
            return;
        }

        StringBuilder summary = new StringBuilder(512);
        summary.append("## PDF Text Layer Audit\n\n");
        summary.append("| PDF | Result | Details |\n");
        summary.append("| --- | --- | --- |\n");
        if (run.files().isEmpty()) {
            summary.append("| — | Passed | No changed PDF files |\n");
        }
        for (ActionRun.AuditedPdf file : run.files()) {
            summary.append("| <code>")
                    .append(escapeHtml(file.repositoryPath()))
                    .append("</code> | ");
            if (!file.succeeded()) {
                summary.append("Error | ")
                        .append(escapeHtml(file.error()));
            } else if (!file.report().needsAttention()) {
                summary.append("Passed | ")
                        .append(file.report().pageCount())
                        .append(" page(s)");
            } else {
                summary.append("Needs attention | ")
                        .append(file.report().pagesNeedingAttention())
                        .append(" of ")
                        .append(file.report().pages().size())
                        .append(" inspected page(s)");
            }
            summary.append(" |\n");
        }
        summary.append("\nFull machine-readable report: <code>")
                .append(escapeHtml(environmentRelativeReportPath()))
                .append("</code>\n");

        Files.writeString(
                Path.of(value),
                summary.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private String environmentRelativeReportPath() {
        String configured = environment.get("INPUT_REPORT_PATH");
        return configured == null || configured.isBlank()
                ? "pdf-text-layer-audit.json"
                : configured;
    }

    private void writeOutputs(ActionRun run, String reportPath) throws IOException {
        String value = environment.get("GITHUB_OUTPUT");
        if (value == null || value.isBlank()) {
            return;
        }
        String outputs = "files_checked=" + run.filesChecked() + "\n"
                + "files_with_findings=" + run.filesWithFindings() + "\n"
                + "files_failed=" + run.filesFailed() + "\n"
                + "report_path=" + reportPath + "\n";
        Files.writeString(
                Path.of(value),
                outputs,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static String escapeProperty(String value) {
        return escapeData(value).replace(":", "%3A").replace(",", "%2C");
    }

    private static String escapeData(String value) {
        return value
                .replace("%", "%25")
                .replace("\r", "%0D")
                .replace("\n", "%0A");
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("|", "&#124;")
                .replace("\r", "&#13;")
                .replace("\n", "&#10;");
    }

    private record Annotation(String level, String file, String title, String message) {
    }
}
