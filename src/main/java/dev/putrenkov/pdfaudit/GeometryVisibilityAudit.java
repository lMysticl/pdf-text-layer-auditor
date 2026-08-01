package dev.putrenkov.pdfaudit;

public record GeometryVisibilityAudit(
        boolean assessed,
        Integer visibleGlyphCount,
        Integer invisibleGlyphCount,
        Integer offPageGlyphCount,
        Integer clippedGlyphCount,
        Integer transparentGlyphCount,
        Integer duplicateOverlapGlyphCount,
        Integer rotatedGlyphCount,
        Integer verticalGlyphCount
) {
    public GeometryVisibilityAudit {
        if (assessed) {
            requireNonNegative(visibleGlyphCount);
            requireNonNegative(invisibleGlyphCount);
            requireNonNegative(offPageGlyphCount);
            requireNonNegative(clippedGlyphCount);
            requireNonNegative(transparentGlyphCount);
            requireNonNegative(duplicateOverlapGlyphCount);
            requireNonNegative(rotatedGlyphCount);
            requireNonNegative(verticalGlyphCount);
        } else if (visibleGlyphCount != null
                || invisibleGlyphCount != null
                || offPageGlyphCount != null
                || clippedGlyphCount != null
                || transparentGlyphCount != null
                || duplicateOverlapGlyphCount != null
                || rotatedGlyphCount != null
                || verticalGlyphCount != null) {
            throw new IllegalArgumentException("Unassessed geometry must not contain counters");
        }
    }

    public static GeometryVisibilityAudit unassessed() {
        return new GeometryVisibilityAudit(
                false, null, null, null, null, null, null, null, null);
    }

    private static void requireNonNegative(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("Assessed geometry counters must be non-negative");
        }
    }
}
