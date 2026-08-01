package dev.putrenkov.pdfaudit;

public record GeometryVisibilityAudit(
        boolean assessed,
        Integer invisibleGlyphCount,
        Integer offPageGlyphCount,
        Integer clippedGlyphCount,
        Integer transparentGlyphCount
) {
    public GeometryVisibilityAudit {
        if (assessed) {
            requireNonNegative(invisibleGlyphCount);
            requireNonNegative(offPageGlyphCount);
            requireNonNegative(clippedGlyphCount);
            requireNonNegative(transparentGlyphCount);
        } else if (invisibleGlyphCount != null
                || offPageGlyphCount != null
                || clippedGlyphCount != null
                || transparentGlyphCount != null) {
            throw new IllegalArgumentException("Unassessed geometry must not contain counters");
        }
    }

    public static GeometryVisibilityAudit unassessed() {
        return new GeometryVisibilityAudit(false, null, null, null, null);
    }

    private static void requireNonNegative(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("Assessed geometry counters must be non-negative");
        }
    }
}
