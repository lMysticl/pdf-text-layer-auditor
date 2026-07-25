package dev.putrenkov.pdfaudit;

import java.nio.file.Path;
import java.util.List;

public record AuditReport(
        Path file,
        long fileSizeBytes,
        int pageCount,
        boolean encrypted,
        boolean extractionAllowed,
        float tinyTextThresholdPoints,
        List<PageAudit> pages
) {
    public AuditReport {
        if (!Float.isFinite(tinyTextThresholdPoints) || tinyTextThresholdPoints < 0) {
            throw new IllegalArgumentException(
                    "tinyTextThresholdPoints must be finite and non-negative");
        }
        pages = List.copyOf(pages);
    }

    public long pagesNeedingAttention() {
        return pages.stream().filter(PageAudit::needsAttention).count();
    }

    public boolean needsAttention() {
        return pagesNeedingAttention() > 0;
    }
}
