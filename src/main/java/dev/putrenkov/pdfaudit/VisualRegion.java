package dev.putrenkov.pdfaudit;

import java.util.Objects;

/**
 * Bounded visual-object evidence in top-left, display-oriented PDF points.
 */
public record VisualRegion(
        VisualRegionType type,
        double xPoints,
        double yPoints,
        double widthPoints,
        double heightPoints
) {
    public VisualRegion {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(xPoints)
                || !Double.isFinite(yPoints)
                || !Double.isFinite(widthPoints)
                || !Double.isFinite(heightPoints)
                || xPoints < 0
                || yPoints < 0
                || widthPoints <= 0
                || heightPoints <= 0) {
            throw new IllegalArgumentException("Visual region coordinates are invalid");
        }
    }
}
