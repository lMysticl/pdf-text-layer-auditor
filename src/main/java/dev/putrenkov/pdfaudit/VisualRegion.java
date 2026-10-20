package dev.putrenkov.pdfaudit;

import java.awt.geom.Rectangle2D;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

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

    /**
     * Converts PDF user-space bounds to a region in the page's displayed orientation,
     * clipped to the crop box and rounded to 0.001 pt; null when it cannot be represented.
     */
    static VisualRegion displayed(
            VisualRegionType type,
            PDRectangle crop,
            int pageRotation,
            Rectangle2D pdfBounds
    ) {
        if (crop == null
                || pdfBounds == null
                || pdfBounds.isEmpty()
                || !Float.isFinite(crop.getLowerLeftX())
                || !Float.isFinite(crop.getLowerLeftY())
                || !Float.isFinite(crop.getWidth())
                || !Float.isFinite(crop.getHeight())
                || !Double.isFinite(pdfBounds.getMinX())
                || !Double.isFinite(pdfBounds.getMinY())
                || !Double.isFinite(pdfBounds.getWidth())
                || !Double.isFinite(pdfBounds.getHeight())
                || crop.getWidth() <= 0
                || crop.getHeight() <= 0) {
            return null;
        }
        int rotation = Math.floorMod(pageRotation, 360);
        if (rotation % 90 != 0) {
            return null;
        }
        double pdfX = pdfBounds.getMinX() - crop.getLowerLeftX();
        double pdfY = pdfBounds.getMinY() - crop.getLowerLeftY();
        double pdfWidth = pdfBounds.getWidth();
        double pdfHeight = pdfBounds.getHeight();
        double displayWidth = rotation == 90 || rotation == 270
                ? crop.getHeight() : crop.getWidth();
        double displayHeight = rotation == 90 || rotation == 270
                ? crop.getWidth() : crop.getHeight();
        double x;
        double y;
        double width;
        double height;
        switch (rotation) {
            case 0 -> {
                x = pdfX;
                y = displayHeight - (pdfY + pdfHeight);
                width = pdfWidth;
                height = pdfHeight;
            }
            case 90 -> {
                x = pdfY;
                y = pdfX;
                width = pdfHeight;
                height = pdfWidth;
            }
            case 180 -> {
                x = displayWidth - (pdfX + pdfWidth);
                y = pdfY;
                width = pdfWidth;
                height = pdfHeight;
            }
            case 270 -> {
                x = displayWidth - (pdfY + pdfHeight);
                y = displayHeight - (pdfX + pdfWidth);
                width = pdfHeight;
                height = pdfWidth;
            }
            default -> throw new IllegalStateException("Unexpected normalized page rotation");
        }
        double boundedX = Math.max(0, Math.min(x, displayWidth));
        double boundedY = Math.max(0, Math.min(y, displayHeight));
        double boundedWidth = Math.max(0, Math.min(width, displayWidth - boundedX));
        double boundedHeight = Math.max(0, Math.min(height, displayHeight - boundedY));
        if (boundedWidth <= 0 || boundedHeight <= 0) {
            return null;
        }
        double roundedX = rounded(boundedX);
        double roundedY = rounded(boundedY);
        double roundedWidth = rounded(boundedWidth);
        double roundedHeight = rounded(boundedHeight);
        if (!Double.isFinite(roundedX)
                || !Double.isFinite(roundedY)
                || !Double.isFinite(roundedWidth)
                || !Double.isFinite(roundedHeight)
                || roundedX < 0
                || roundedY < 0
                || roundedWidth <= 0
                || roundedHeight <= 0) {
            return null;
        }
        return new VisualRegion(
                type,
                roundedX,
                roundedY,
                roundedWidth,
                roundedHeight);
    }

    private static double rounded(double value) {
        return Math.rint(value * 1_000d) / 1_000d;
    }
}
