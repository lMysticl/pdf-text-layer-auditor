package dev.putrenkov.pdfaudit;

public record SemanticMappingAudit(
        int rawMappedGlyphCount,
        int rawUnmappedGlyphCount,
        int actualTextResolvedGlyphCount,
        int malformedToUnicodeFontCount
) {
    public SemanticMappingAudit {
        if (rawMappedGlyphCount < 0
                || rawUnmappedGlyphCount < 0
                || actualTextResolvedGlyphCount < 0
                || malformedToUnicodeFontCount < 0
                || actualTextResolvedGlyphCount > rawUnmappedGlyphCount) {
            throw new IllegalArgumentException("Semantic mapping counters are invalid");
        }
    }
}
