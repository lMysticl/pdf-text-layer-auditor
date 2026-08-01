package dev.putrenkov.pdfaudit;

public final class AuditWorkLimitException extends IllegalArgumentException {
    private final Code code;

    public AuditWorkLimitException(Code code, long limit) {
        super("PDF exceeds the configured " + code.label + " limit of " + limit);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        GLYPH_COUNT("glyph-count"),
        SEMANTIC_CHARACTER_COUNT("semantic-character-count"),
        FONT_COUNT("font-count"),
        IMAGE_COUNT("image-count"),
        PAINTED_VECTOR_PATH_COUNT("painted-vector-path-count"),
        ANNOTATION_COUNT("annotation-count"),
        ANNOTATION_APPEARANCE_STREAM_COUNT("annotation-appearance-stream-count"),
        OPTIONAL_CONTENT_REFERENCE_COUNT("optional-content-reference-count"),
        DOCUMENT_SURFACE_COUNT("document-surface-count");

        private final String label;

        Code(String label) {
            this.label = label;
        }
    }
}
