package dev.putrenkov.pdfaudit;

public record OptionalContentAudit(
        boolean complete,
        int referenceCount,
        int membershipReferenceCount,
        int hiddenInViewReferenceCount,
        int hiddenInPrintReferenceCount,
        int hiddenInExportReferenceCount,
        int evaluationFailureCount
) {
    public OptionalContentAudit {
        if (referenceCount < 0
                || membershipReferenceCount < 0
                || hiddenInViewReferenceCount < 0
                || hiddenInPrintReferenceCount < 0
                || hiddenInExportReferenceCount < 0
                || evaluationFailureCount < 0
                || membershipReferenceCount > referenceCount
                || hiddenInViewReferenceCount > referenceCount
                || hiddenInPrintReferenceCount > referenceCount
                || hiddenInExportReferenceCount > referenceCount
                || complete != (evaluationFailureCount == 0)) {
            throw new IllegalArgumentException("Optional-content observations are invalid");
        }
    }
}
