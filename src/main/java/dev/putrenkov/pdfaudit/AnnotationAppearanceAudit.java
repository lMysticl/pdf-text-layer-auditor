package dev.putrenkov.pdfaudit;

public record AnnotationAppearanceAudit(
        boolean assessed,
        Integer appearanceStreamCount,
        Integer glyphCount,
        Integer unicodeCharacterCount,
        Integer missingUnicodeGlyphCount,
        Integer replacementCharacterCount
) {
    public AnnotationAppearanceAudit {
        if (assessed) {
            requireCounter(appearanceStreamCount);
            requireCounter(glyphCount);
            requireCounter(unicodeCharacterCount);
            requireCounter(missingUnicodeGlyphCount);
            requireCounter(replacementCharacterCount);
            if (missingUnicodeGlyphCount > glyphCount) {
                throw new IllegalArgumentException(
                        "Missing annotation mappings exceed annotation glyphs");
            }
        } else if (appearanceStreamCount != null
                || glyphCount != null
                || unicodeCharacterCount != null
                || missingUnicodeGlyphCount != null
                || replacementCharacterCount != null) {
            throw new IllegalArgumentException(
                    "Unassessed annotation appearances must not contain counters");
        }
    }

    public static AnnotationAppearanceAudit unassessed() {
        return new AnnotationAppearanceAudit(false, null, null, null, null, null);
    }

    private static void requireCounter(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(
                    "Annotation appearance counters must be non-negative");
        }
    }
}
