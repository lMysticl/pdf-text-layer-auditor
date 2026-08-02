package dev.putrenkov.pdfaudit;

import java.util.Objects;

/**
 * Bounded location evidence for one page finding. Coordinates use PDF points
 * in a top-left, display-oriented page coordinate system and never contain
 * document text.
 */
public record FindingLocation(
        Finding code,
        double xPoints,
        double yPoints,
        double widthPoints,
        double heightPoints
) {
    public FindingLocation {
        Objects.requireNonNull(code, "code");
        if (!Double.isFinite(xPoints)
                || !Double.isFinite(yPoints)
                || !Double.isFinite(widthPoints)
                || !Double.isFinite(heightPoints)
                || widthPoints < 0
                || heightPoints < 0) {
            throw new IllegalArgumentException("Finding location coordinates are invalid");
        }
    }
}
