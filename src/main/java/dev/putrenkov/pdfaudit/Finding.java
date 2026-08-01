package dev.putrenkov.pdfaudit;

public enum Finding {
    NO_TEXT_LAYER("No native text glyphs were found; the page may be blank or image-only."),
    MISSING_UNICODE("Some glyphs have no usable Unicode mapping."),
    REPLACEMENT_CHARACTERS("Extracted text contains Unicode replacement characters."),
    TINY_TEXT("Some text is below the configured size threshold and may be hidden or unreliable."),
    MALFORMED_TOUNICODE_CMAP("A declared ToUnicode CMap could not be parsed reliably."),
    INVALID_TOUNICODE_CMAP("A declared ToUnicode CMap contains no usable Unicode mappings."),
    IMPLICIT_COMPOSITE_UNICODE_MAPPING(
            "A composite font has no explicit ToUnicode map; extracted Unicode may be inferred rather than authored."),
    READING_ORDER_DIVERGENCE(
            "Content-stream and position-based extraction produce different character order."),
    SPARSE_TEXT_OVER_FULL_PAGE_IMAGE(
            "A near-full-page image has only a small extracted text layer; OCR may be incomplete."),
    PARTIAL_TEXT_OVER_FULL_PAGE_IMAGE(
            "Visible text occupies too little of a near-full-page image; the text layer may be partial."),
    ANNOTATION_MISSING_UNICODE(
            "Some annotation appearance glyphs have no usable Unicode mapping."),
    ANNOTATION_REPLACEMENT_CHARACTERS(
            "Annotation appearance text contains Unicode replacement characters.");

    private final String description;

    Finding(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
