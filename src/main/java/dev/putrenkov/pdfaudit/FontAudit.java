package dev.putrenkov.pdfaudit;

public record FontAudit(
        String name,
        boolean embedded,
        boolean damaged,
        int glyphCount
) {
}
