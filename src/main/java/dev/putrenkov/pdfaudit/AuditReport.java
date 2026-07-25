package dev.putrenkov.pdfaudit;

import java.nio.file.Path;
import java.util.List;

public record AuditReport(
        Path file,
        long fileSizeBytes,
        int pageCount,
        boolean encrypted,
        boolean extractionAllowed,
        List<PageAudit> pages
) {
    public AuditReport {
        pages = List.copyOf(pages);
    }

    public long pagesNeedingAttention() {
        return pages.stream().filter(PageAudit::needsAttention).count();
    }

    public boolean needsAttention() {
        return pagesNeedingAttention() > 0;
    }
}
