package dev.putrenkov.pdfaudit;

import java.util.Objects;

public record ParseDiagnostic(
        ParseDiagnosticCode code,
        int pageNumber,
        String fontName
) {
    public ParseDiagnostic {
        Objects.requireNonNull(code, "code");
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        Objects.requireNonNull(fontName, "fontName");
    }
}
