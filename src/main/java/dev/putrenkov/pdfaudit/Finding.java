package dev.putrenkov.pdfaudit;

public enum Finding {
    NO_TEXT_LAYER("No native text glyphs were found; the page may be blank or image-only."),
    MISSING_UNICODE("Some glyphs have no usable Unicode mapping."),
    REPLACEMENT_CHARACTERS("Extracted text contains Unicode replacement characters."),
    TINY_TEXT("Some text is below the configured size threshold and may be hidden or unreliable."),
    MALFORMED_TOUNICODE_CMAP("A declared ToUnicode CMap could not be parsed reliably."),
    INVALID_TOUNICODE_CMAP("A declared ToUnicode CMap contains no usable Unicode mappings."),
    READING_ORDER_DIVERGENCE(
            "Content-stream and position-based extraction produce different character order."),
    SPARSE_TEXT_OVER_FULL_PAGE_IMAGE(
            "A near-full-page image has only a small extracted text layer; OCR may be incomplete.");

    private final String description;

    Finding(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
