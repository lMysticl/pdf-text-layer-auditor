package dev.putrenkov.pdfaudit;

public record ReadingOrderAudit(
        boolean assessed,
        boolean diverges,
        int streamCharacterCount,
        int positionCharacterCount
) {
    public ReadingOrderAudit {
        if ((!assessed && diverges)
                || streamCharacterCount < 0
                || positionCharacterCount < 0) {
            throw new IllegalArgumentException("Reading-order evidence is invalid");
        }
    }
}
