package dev.putrenkov.pdfaudit.github;

import dev.putrenkov.pdfaudit.AuditReport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class GitHubActionRunner {
    private final Auditor auditor;

    GitHubActionRunner(Auditor auditor) {
        this.auditor = Objects.requireNonNull(auditor, "auditor");
    }

    ActionRun run(
            List<ChangedPdfSelector.ChangedPdf> changedPdfs,
            boolean failOnFindings
    ) {
        List<ActionRun.AuditedPdf> results = new ArrayList<>(changedPdfs.size());
        for (ChangedPdfSelector.ChangedPdf changedPdf : changedPdfs) {
            try {
                results.add(ActionRun.AuditedPdf.success(
                        changedPdf.repositoryPath(),
                        auditor.audit(changedPdf.localPath())));
            } catch (IllegalArgumentException | SecurityException | IOException exception) {
                results.add(ActionRun.AuditedPdf.failure(
                        changedPdf.repositoryPath(),
                        safeMessage(exception)));
            }
        }
        return new ActionRun(results, failOnFindings);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').strip();
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500);
    }

    @FunctionalInterface
    interface Auditor {
        AuditReport audit(java.nio.file.Path path) throws IOException;
    }
}
