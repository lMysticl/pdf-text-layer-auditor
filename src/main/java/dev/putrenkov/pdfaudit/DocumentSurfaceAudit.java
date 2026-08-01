package dev.putrenkov.pdfaudit;

public record DocumentSurfaceAudit(
        boolean assessed,
        boolean complete,
        Integer acroFormFieldCount,
        Integer signatureFieldCount,
        Integer widgetWithoutAppearanceCount,
        Boolean xfaPresent,
        Integer embeddedFileCount,
        Integer associatedFileReferenceCount,
        Boolean portfolioPresent
) {
    public DocumentSurfaceAudit {
        if (assessed) {
            requireCounter(acroFormFieldCount);
            requireCounter(signatureFieldCount);
            requireCounter(widgetWithoutAppearanceCount);
            requireCounter(embeddedFileCount);
            requireCounter(associatedFileReferenceCount);
            if (signatureFieldCount > acroFormFieldCount
                    || xfaPresent == null
                    || portfolioPresent == null) {
                throw new IllegalArgumentException("Document-surface evidence is invalid");
            }
        } else if (complete
                || acroFormFieldCount != null
                || signatureFieldCount != null
                || widgetWithoutAppearanceCount != null
                || xfaPresent != null
                || embeddedFileCount != null
                || associatedFileReferenceCount != null
                || portfolioPresent != null) {
            throw new IllegalArgumentException(
                    "Unassessed document surfaces must not contain observations");
        }
    }

    public static DocumentSurfaceAudit unassessed() {
        return new DocumentSurfaceAudit(
                false, false, null, null, null, null, null, null, null);
    }

    public boolean requiresProfile() {
        return !complete
                || acroFormFieldCount > 0
                || xfaPresent
                || embeddedFileCount > 0
                || associatedFileReferenceCount > 0
                || portfolioPresent;
    }

    private static void requireCounter(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("Document-surface counter is invalid");
        }
    }
}
