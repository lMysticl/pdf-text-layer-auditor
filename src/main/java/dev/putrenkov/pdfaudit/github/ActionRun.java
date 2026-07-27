package dev.putrenkov.pdfaudit.github;

import dev.putrenkov.pdfaudit.AuditReport;
import java.util.List;

record ActionRun(List<AuditedPdf> files, boolean failOnFindings) {
    ActionRun {
        files = List.copyOf(files);
    }

    int filesChecked() {
        return (int) files.stream().filter(AuditedPdf::succeeded).count();
    }

    int filesWithFindings() {
        return (int) files.stream()
                .filter(AuditedPdf::succeeded)
                .filter(file -> file.report().needsAttention())
                .count();
    }

    int filesFailed() {
        return (int) files.stream().filter(file -> !file.succeeded()).count();
    }

    int exitCode() {
        if (filesFailed() > 0) {
            return 2;
        }
        if (failOnFindings && filesWithFindings() > 0) {
            return 1;
        }
        return 0;
    }

    record AuditedPdf(String repositoryPath, AuditReport report, String error) {
        AuditedPdf {
            if ((report == null) == (error == null)) {
                throw new IllegalArgumentException(
                        "Exactly one of report and error must be provided");
            }
        }

        static AuditedPdf success(String repositoryPath, AuditReport report) {
            return new AuditedPdf(repositoryPath, report, null);
        }

        static AuditedPdf failure(String repositoryPath, String error) {
            return new AuditedPdf(repositoryPath, null, error);
        }

        boolean succeeded() {
            return report != null;
        }
    }
}
