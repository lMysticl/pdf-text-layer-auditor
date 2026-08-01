package dev.putrenkov.pdfaudit;

import java.util.List;

public record ParseHealth(
        boolean complete,
        boolean recovered,
        int parserWarningCount,
        List<ParseDiagnostic> diagnostics
) {
    public ParseHealth {
        diagnostics = List.copyOf(diagnostics);
        if (parserWarningCount < 0 || !complete && recovered) {
            throw new IllegalArgumentException("Incomplete parsing cannot be reported as recovered");
        }
    }
}
