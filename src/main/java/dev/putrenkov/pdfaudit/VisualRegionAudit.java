package dev.putrenkov.pdfaudit;

import java.util.List;

/**
 * Exact visual-object count plus a bounded, text-free location sample.
 */
public record VisualRegionAudit(
        long totalRegionCount,
        boolean regionsTruncated,
        List<VisualRegion> regions
) {
    public VisualRegionAudit {
        regions = List.copyOf(regions);
        if (totalRegionCount < 0 || totalRegionCount < regions.size()) {
            throw new IllegalArgumentException("Visual region counters are invalid");
        }
        if (regionsTruncated != (totalRegionCount > regions.size())) {
            throw new IllegalArgumentException("Visual region truncation flag is invalid");
        }
    }

    public static VisualRegionAudit of(long totalRegionCount, List<VisualRegion> regions) {
        return new VisualRegionAudit(
                totalRegionCount,
                totalRegionCount > regions.size(),
                regions);
    }

    public static VisualRegionAudit empty() {
        return new VisualRegionAudit(0, false, List.of());
    }
}
