package dev.putrenkov.pdfaudit;

import java.util.List;

/**
 * Page geometry plus a bounded, text-free sample of finding locations.
 */
public record SpatialEvidenceAudit(
        boolean assessed,
        Double pageWidthPoints,
        Double pageHeightPoints,
        Integer rotationDegrees,
        String coordinateSpace,
        long totalLocationCount,
        boolean locationsTruncated,
        List<FindingLocation> locations,
        VisualRegionAudit visualRegions
) {
    public static final String TOP_LEFT_DISPLAY_POINTS = "TOP_LEFT_DISPLAY_POINTS";

    public SpatialEvidenceAudit {
        locations = List.copyOf(locations);
        if (visualRegions == null) {
            throw new IllegalArgumentException("Visual region evidence is required");
        }
        if (totalLocationCount < 0 || totalLocationCount < locations.size()) {
            throw new IllegalArgumentException("Spatial evidence counters are invalid");
        }
        if (locationsTruncated != (totalLocationCount > locations.size())) {
            throw new IllegalArgumentException("Spatial evidence truncation flag is invalid");
        }
        if (!assessed) {
            if (pageWidthPoints != null
                    || pageHeightPoints != null
                    || rotationDegrees != null
                    || coordinateSpace != null
                    || totalLocationCount != 0
                    || !locations.isEmpty()
                    || visualRegions.totalRegionCount() != 0) {
                throw new IllegalArgumentException("Unassessed spatial evidence must be empty");
            }
        } else if (pageWidthPoints == null
                || pageHeightPoints == null
                || rotationDegrees == null
                || !TOP_LEFT_DISPLAY_POINTS.equals(coordinateSpace)
                || !Double.isFinite(pageWidthPoints)
                || !Double.isFinite(pageHeightPoints)
                || pageWidthPoints <= 0
                || pageHeightPoints <= 0
                || !List.of(0, 90, 180, 270).contains(rotationDegrees)) {
            throw new IllegalArgumentException("Assessed page geometry is invalid");
        }
    }

    public SpatialEvidenceAudit(
            boolean assessed,
            Double pageWidthPoints,
            Double pageHeightPoints,
            Integer rotationDegrees,
            String coordinateSpace,
            long totalLocationCount,
            boolean locationsTruncated,
            List<FindingLocation> locations
    ) {
        this(
                assessed,
                pageWidthPoints,
                pageHeightPoints,
                rotationDegrees,
                coordinateSpace,
                totalLocationCount,
                locationsTruncated,
                locations,
                VisualRegionAudit.empty());
    }

    public static SpatialEvidenceAudit assessed(
            double pageWidthPoints,
            double pageHeightPoints,
            int rotationDegrees,
            long totalLocationCount,
            List<FindingLocation> locations,
            VisualRegionAudit visualRegions
    ) {
        return new SpatialEvidenceAudit(
                true,
                pageWidthPoints,
                pageHeightPoints,
                rotationDegrees,
                TOP_LEFT_DISPLAY_POINTS,
                totalLocationCount,
                totalLocationCount > locations.size(),
                locations,
                visualRegions);
    }

    public static SpatialEvidenceAudit assessed(
            double pageWidthPoints,
            double pageHeightPoints,
            int rotationDegrees,
            long totalLocationCount,
            List<FindingLocation> locations
    ) {
        return assessed(
                pageWidthPoints,
                pageHeightPoints,
                rotationDegrees,
                totalLocationCount,
                locations,
                VisualRegionAudit.empty());
    }

    public static SpatialEvidenceAudit unassessed() {
        return new SpatialEvidenceAudit(
                false,
                null,
                null,
                null,
                null,
                0,
                false,
                List.of(),
                VisualRegionAudit.empty());
    }
}
