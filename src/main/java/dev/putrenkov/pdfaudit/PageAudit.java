package dev.putrenkov.pdfaudit;

import java.util.List;

public record PageAudit(
        int pageNumber,
        int glyphCount,
        int unicodeCharacterCount,
        int missingUnicodeGlyphCount,
        int replacementCharacterCount,
        int tinyTextGlyphCount,
        List<FontAudit> fonts,
        List<Finding> findings
) {
    public PageAudit {
        fonts = List.copyOf(fonts);
        findings = List.copyOf(findings);
    }

    public boolean needsAttention() {
        return !findings.isEmpty();
    }
}
