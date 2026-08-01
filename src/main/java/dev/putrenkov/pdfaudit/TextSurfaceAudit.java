package dev.putrenkov.pdfaudit;

public record TextSurfaceAudit(
        int pageContentGlyphCount,
        int formXObjectGlyphCount,
        int actualTextGlyphCount,
        int actualTextCharacterCount
) {
    public TextSurfaceAudit {
        if (pageContentGlyphCount < 0
                || formXObjectGlyphCount < 0
                || actualTextGlyphCount < 0
                || actualTextCharacterCount < 0) {
            throw new IllegalArgumentException("Text surface counters must be non-negative");
        }
    }
}
