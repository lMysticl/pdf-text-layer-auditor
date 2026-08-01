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
        ParseHealth parseHealth,
        EvidenceCompleteness completeness,
        DocumentSurfaceAudit documentSurfaces,
        List<PageAudit> pages
) {
    public AuditReport {
        if (!Float.isFinite(tinyTextThresholdPoints) || tinyTextThresholdPoints < 0) {
            throw new IllegalArgumentException(
                    "tinyTextThresholdPoints must be finite and non-negative");
        }
        java.util.Objects.requireNonNull(parseHealth, "parseHealth");
        java.util.Objects.requireNonNull(completeness, "completeness");
        java.util.Objects.requireNonNull(documentSurfaces, "documentSurfaces");
        pages = List.copyOf(pages);
    }

    public AuditReport(
            Path file,
            long fileSizeBytes,
            int pageCount,
            boolean encrypted,
            boolean extractionAllowed,
            float tinyTextThresholdPoints,
            List<PageAudit> pages
    ) {
        this(
                file,
                fileSizeBytes,
                pageCount,
                encrypted,
                extractionAllowed,
                tinyTextThresholdPoints,
                new ParseHealth(true, false, 0, List.of()),
                EvidenceCompleteness.phaseZero(),
                DocumentSurfaceAudit.unassessed(),
                pages);
    }

    public long pagesNeedingAttention() {
        return pages.stream().filter(PageAudit::needsAttention).count();
    }

    public boolean needsAttention() {
        return parseHealth.recovered()
                || !parseHealth.complete()
                || documentSurfaces.requiresProfile()
                || pagesNeedingAttention() > 0;
    }
}
