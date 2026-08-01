package dev.putrenkov.pdfaudit;

import java.util.List;

public record PageAudit(
        int pageNumber,
        int glyphCount,
        int unicodeCharacterCount,
        int missingUnicodeGlyphCount,
        int replacementCharacterCount,
        int tinyTextGlyphCount,
        PageClassification classification,
        TextSurfaceAudit textSurfaces,
        SemanticMappingAudit semanticMapping,
        ReadingOrderAudit readingOrder,
        GeometryVisibilityAudit geometryVisibility,
        VisualContentAudit visualContent,
        AnnotationAppearanceAudit annotationAppearances,
        OptionalContentAudit optionalContent,
        UnicodeProfileAudit unicodeProfile,
        List<FontAudit> fonts,
        List<Finding> findings
) {
    public PageAudit {
        if (pageNumber < 1
                || glyphCount < 0
                || unicodeCharacterCount < 0
                || missingUnicodeGlyphCount < 0
                || replacementCharacterCount < 0
                || tinyTextGlyphCount < 0) {
            throw new IllegalArgumentException("Page audit counters are invalid");
        }
        java.util.Objects.requireNonNull(classification, "classification");
        java.util.Objects.requireNonNull(textSurfaces, "textSurfaces");
        java.util.Objects.requireNonNull(semanticMapping, "semanticMapping");
        java.util.Objects.requireNonNull(readingOrder, "readingOrder");
        java.util.Objects.requireNonNull(geometryVisibility, "geometryVisibility");
        java.util.Objects.requireNonNull(visualContent, "visualContent");
        java.util.Objects.requireNonNull(annotationAppearances, "annotationAppearances");
        java.util.Objects.requireNonNull(optionalContent, "optionalContent");
        java.util.Objects.requireNonNull(unicodeProfile, "unicodeProfile");
        fonts = List.copyOf(fonts);
        findings = List.copyOf(findings);
    }

    public PageAudit(
            int pageNumber,
            int glyphCount,
            int unicodeCharacterCount,
            int missingUnicodeGlyphCount,
            int replacementCharacterCount,
            int tinyTextGlyphCount,
            List<FontAudit> fonts,
            List<Finding> findings
    ) {
        this(
                pageNumber,
                glyphCount,
                unicodeCharacterCount,
                missingUnicodeGlyphCount,
                replacementCharacterCount,
                tinyTextGlyphCount,
                PageClassification.UNKNOWN,
                new TextSurfaceAudit(glyphCount, 0, 0, 0),
                new SemanticMappingAudit(
                        Math.max(0, glyphCount - missingUnicodeGlyphCount),
                        missingUnicodeGlyphCount,
                        0,
                        0,
                        0),
                new ReadingOrderAudit(
                        false,
                        false,
                        unicodeCharacterCount,
                        unicodeCharacterCount),
                GeometryVisibilityAudit.unassessed(),
                VisualContentAudit.unassessed(),
                AnnotationAppearanceAudit.unassessed(),
                new OptionalContentAudit(true, 0, 0, 0, 0, 0, 0),
                new UnicodeProfileAudit(List.of(), 0, 0, 0, 0, 0, 0),
                fonts,
                findings);
    }

    public boolean needsAttention() {
        return !findings.isEmpty();
    }
}
