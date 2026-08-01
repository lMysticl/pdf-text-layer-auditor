package dev.putrenkov.pdfaudit;

public record FontAudit(
        String name,
        String subtype,
        String encoding,
        boolean embedded,
        boolean damaged,
        boolean vertical,
        boolean toUnicodePresent,
        boolean subset,
        int glyphCount,
        int rawUnmappedGlyphCount
) {
    public FontAudit {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(subtype, "subtype");
        java.util.Objects.requireNonNull(encoding, "encoding");
        if (name.isBlank()
                || subtype.isBlank()
                || encoding.isBlank()
                || glyphCount < 0
                || rawUnmappedGlyphCount < 0
                || rawUnmappedGlyphCount > glyphCount) {
            throw new IllegalArgumentException("Font audit fields are invalid");
        }
    }

    public FontAudit(
            String name,
            boolean embedded,
            boolean damaged,
            int glyphCount
    ) {
        this(
                name,
                "<unknown>",
                "<unknown>",
                embedded,
                damaged,
                false,
                false,
                false,
                glyphCount,
                0);
    }
}
