package dev.putrenkov.pdfaudit;

/**
 * Exact count of visible, locatable page regions by supported object type.
 */
public record VisualRegionCounts(
        long imageCount,
        long annotationCount,
        long formFieldCount,
        long vectorPathCount
) {
    public VisualRegionCounts {
        if (imageCount < 0
                || annotationCount < 0
                || formFieldCount < 0
                || vectorPathCount < 0) {
            throw new IllegalArgumentException("Visual region counts must be non-negative");
        }
    }

    public long totalRegionCount() {
        return Math.addExact(
                Math.addExact(Math.addExact(imageCount, annotationCount), formFieldCount),
                vectorPathCount);
    }

    public long count(VisualRegionType type) {
        return switch (java.util.Objects.requireNonNull(type, "type")) {
            case IMAGE -> imageCount;
            case ANNOTATION -> annotationCount;
            case FORM_FIELD -> formFieldCount;
            case VECTOR_PATH -> vectorPathCount;
        };
    }

    public static VisualRegionCounts empty() {
        return new VisualRegionCounts(0, 0, 0, 0);
    }
}
