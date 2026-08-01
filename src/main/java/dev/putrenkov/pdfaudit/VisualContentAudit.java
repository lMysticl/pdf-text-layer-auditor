package dev.putrenkov.pdfaudit;

public record VisualContentAudit(
        boolean assessed,
        Integer imageCount,
        Double maxImageCoverageRatio,
        Double combinedImageCoverageRatio,
        Integer imageOccupiedGridCellCount,
        Integer imageTextOverlapGridCellCount,
        Double imageTextOverlapRatio,
        Integer paintedVectorPathCount,
        Integer annotationCount,
        Integer widgetAnnotationCount,
        Boolean optionalContentPresent
) {
    public VisualContentAudit {
        if (assessed) {
            requireNonNegative(imageCount);
            requireNonNegative(paintedVectorPathCount);
            requireNonNegative(annotationCount);
            requireNonNegative(widgetAnnotationCount);
            if (maxImageCoverageRatio == null
                    || !Double.isFinite(maxImageCoverageRatio)
                    || maxImageCoverageRatio < 0
                    || maxImageCoverageRatio > 1) {
                throw new IllegalArgumentException(
                        "Image coverage ratio must be finite and between zero and one");
            }
            if (combinedImageCoverageRatio == null
                    || !Double.isFinite(combinedImageCoverageRatio)
                    || combinedImageCoverageRatio < 0
                    || combinedImageCoverageRatio > 1
                    || combinedImageCoverageRatio < maxImageCoverageRatio) {
                throw new IllegalArgumentException(
                        "Combined image coverage ratio is invalid");
            }
            requireNonNegative(imageOccupiedGridCellCount);
            requireNonNegative(imageTextOverlapGridCellCount);
            if (imageOccupiedGridCellCount > 64
                    || imageTextOverlapGridCellCount > imageOccupiedGridCellCount
                    || imageTextOverlapRatio == null
                    || !Double.isFinite(imageTextOverlapRatio)
                    || imageTextOverlapRatio < 0
                    || imageTextOverlapRatio > 1) {
                throw new IllegalArgumentException(
                        "Image/text spatial coverage is invalid");
            }
            if (optionalContentPresent == null) {
                throw new IllegalArgumentException(
                        "Assessed visual content must declare optional-content presence");
            }
        } else if (imageCount != null
                || maxImageCoverageRatio != null
                || combinedImageCoverageRatio != null
                || imageOccupiedGridCellCount != null
                || imageTextOverlapGridCellCount != null
                || imageTextOverlapRatio != null
                || paintedVectorPathCount != null
                || annotationCount != null
                || widgetAnnotationCount != null
                || optionalContentPresent != null) {
            throw new IllegalArgumentException(
                    "Unassessed visual content must not contain observations");
        }
    }

    public static VisualContentAudit unassessed() {
        return new VisualContentAudit(
                false, null, null, null, null, null, null, null, null, null, null);
    }

    private static void requireNonNegative(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(
                    "Assessed visual-content counters must be non-negative");
        }
    }
}
