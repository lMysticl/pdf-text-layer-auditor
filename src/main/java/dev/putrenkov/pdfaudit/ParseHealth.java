package dev.putrenkov.pdfaudit;

import java.util.List;

public record ParseHealth(
        boolean complete,
        boolean recovered,
        List<ParseDiagnostic> diagnostics
) {
    public ParseHealth {
        diagnostics = List.copyOf(diagnostics);
        if (!complete && recovered) {
            throw new IllegalArgumentException("Incomplete parsing cannot be reported as recovered");
        }
    }
}
