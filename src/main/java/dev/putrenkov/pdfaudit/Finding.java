package dev.putrenkov.pdfaudit;

public enum Finding {
    NO_TEXT_LAYER("No native text glyphs were found; the page may be blank or image-only."),
    MISSING_UNICODE("Some glyphs have no usable Unicode mapping."),
    REPLACEMENT_CHARACTERS("Extracted text contains Unicode replacement characters."),
    TINY_TEXT("Some text is below the configured size threshold and may be hidden or unreliable.");

    private final String description;

    Finding(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
