package dev.putrenkov.pdfaudit;

public record EvidenceCompleteness(
        boolean pageContent,
        boolean formXObjects,
        boolean semanticMappings,
        boolean readingOrder,
        boolean geometryVisibility,
        boolean annotations,
        boolean optionalContent
) {
    public static EvidenceCompleteness phaseZero() {
        return new EvidenceCompleteness(true, true, true, true, false, false, false);
    }

    public static EvidenceCompleteness phaseOne() {
        return new EvidenceCompleteness(true, true, true, true, true, false, false);
    }

    public boolean completeForDirectRouting() {
        return pageContent
                && formXObjects
                && semanticMappings
                && readingOrder
                && geometryVisibility
                && annotations
                && optionalContent;
    }
}
