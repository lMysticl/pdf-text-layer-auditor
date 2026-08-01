package dev.putrenkov.pdfaudit;

public enum PageClassification {
    UNKNOWN,
    BLANK,
    VECTOR_ONLY,
    IMAGE_ONLY,
    NATIVE_TEXT,
    MIXED,
    SPARSE_OCR,
    PARTIAL_OCR
}
