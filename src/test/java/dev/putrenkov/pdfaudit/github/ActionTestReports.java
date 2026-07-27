package dev.putrenkov.pdfaudit.github;

import dev.putrenkov.pdfaudit.AuditReport;
import dev.putrenkov.pdfaudit.Finding;
import dev.putrenkov.pdfaudit.PageAudit;
import java.nio.file.Path;
import java.util.List;

final class ActionTestReports {
    private ActionTestReports() {
    }

    static AuditReport passed(Path path) {
        return report(path, List.of());
    }

    static AuditReport withNoTextLayer(Path path) {
        return report(path, List.of(Finding.NO_TEXT_LAYER));
    }

    private static AuditReport report(Path path, List<Finding> findings) {
        PageAudit page = new PageAudit(
                1,
                findings.isEmpty() ? 10 : 0,
                findings.isEmpty() ? 10 : 0,
                0,
                0,
                0,
                List.of(),
                findings);
        return new AuditReport(
                path,
                100,
                1,
                false,
                true,
                3,
                List.of(page));
    }
}
