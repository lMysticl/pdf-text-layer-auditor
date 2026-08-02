package dev.putrenkov.pdfaudit;

import java.util.List;

/**
 * Exact visual-object count plus a bounded, text-free location sample.
 */
public record VisualRegionAudit(
        long totalRegionCount,
        boolean regionsTruncated,
        VisualRegionCounts counts,
        List<VisualRegion> regions
) {
    public VisualRegionAudit {
        java.util.Objects.requireNonNull(counts, "counts");
        regions = List.copyOf(regions);
        if (totalRegionCount < 0
                || totalRegionCount != counts.totalRegionCount()
                || totalRegionCount < regions.size()) {
            throw new IllegalArgumentException("Visual region counters are invalid");
        }
        if (regionsTruncated != (totalRegionCount > regions.size())) {
            throw new IllegalArgumentException("Visual region truncation flag is invalid");
        }
        for (VisualRegionType type : VisualRegionType.values()) {
            long stored = regions.stream().filter(region -> region.type() == type).count();
            if (stored > counts.count(type)) {
                throw new IllegalArgumentException("Visual region type counters are invalid");
            }
        }
    }

    public VisualRegionAudit(
            long totalRegionCount,
            boolean regionsTruncated,
            List<VisualRegion> regions
    ) {
        this(
                totalRegionCount,
                regionsTruncated,
                new VisualRegionCounts(totalRegionCount, 0, 0),
                regions);
    }

    public static VisualRegionAudit of(
            VisualRegionCounts counts,
            List<VisualRegion> regions
    ) {
        long totalRegionCount = counts.totalRegionCount();
        return new VisualRegionAudit(
                totalRegionCount,
                totalRegionCount > regions.size(),
                counts,
                regions);
    }

    public static VisualRegionAudit of(long totalRegionCount, List<VisualRegion> regions) {
        return of(new VisualRegionCounts(totalRegionCount, 0, 0), regions);
    }

    public static VisualRegionAudit empty() {
        return new VisualRegionAudit(0, false, VisualRegionCounts.empty(), List.of());
    }
}
