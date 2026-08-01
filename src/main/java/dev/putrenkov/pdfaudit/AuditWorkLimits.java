package dev.putrenkov.pdfaudit;

public record AuditWorkLimits(
        long maximumGlyphCount,
        long maximumSemanticCharacterCount,
        int maximumFontCount,
        int maximumImageCount,
        int maximumPaintedVectorPathCount,
        int maximumAnnotationCount,
        int maximumAnnotationAppearanceStreamCount,
        int maximumOptionalContentReferenceCount
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
                || maximumOptionalContentReferenceCount < 1) {
            throw new IllegalArgumentException("Audit work limits must be positive and bounded");
        }
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
                100_000);
    }
}
