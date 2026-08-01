package dev.putrenkov.pdfaudit;

public record AuditWorkLimits(
        long maximumGlyphCount,
        long maximumSemanticCharacterCount,
        int maximumFontCount,
        int maximumImageCount,
        int maximumPaintedVectorPathCount,
        int maximumAnnotationCount,
        int maximumAnnotationAppearanceStreamCount,
        int maximumOptionalContentReferenceCount,
        int maximumDocumentSurfaceCount
) {
    public AuditWorkLimits {
        if (maximumGlyphCount < 1
                || maximumGlyphCount > Integer.MAX_VALUE
                || maximumSemanticCharacterCount < 1
                || maximumSemanticCharacterCount > Integer.MAX_VALUE
                || maximumFontCount < 1
                || maximumImageCount < 1
                || maximumPaintedVectorPathCount < 1
                || maximumAnnotationCount < 1
                || maximumAnnotationAppearanceStreamCount < 1
                || maximumOptionalContentReferenceCount < 1
                || maximumDocumentSurfaceCount < 1) {
            throw new IllegalArgumentException("Audit work limits must be positive and bounded");
        }
    }

    public AuditWorkLimits(
            long maximumGlyphCount,
            long maximumSemanticCharacterCount,
            int maximumFontCount,
            int maximumImageCount,
            int maximumPaintedVectorPathCount,
            int maximumAnnotationCount,
            int maximumAnnotationAppearanceStreamCount,
            int maximumOptionalContentReferenceCount
    ) {
        this(
                maximumGlyphCount,
                maximumSemanticCharacterCount,
                maximumFontCount,
                maximumImageCount,
                maximumPaintedVectorPathCount,
                maximumAnnotationCount,
                maximumAnnotationAppearanceStreamCount,
                maximumOptionalContentReferenceCount,
                100_000);
    }

    public static AuditWorkLimits defaults() {
        return new AuditWorkLimits(
                1_000_000,
                5_000_000,
                10_000,
                100_000,
                1_000_000,
                100_000,
                100_000,
                100_000,
                100_000);
    }
}
