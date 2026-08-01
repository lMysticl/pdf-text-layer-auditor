package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class PdfBoxDiagnosticCaptureTest {
    private static final Logger PDFBOX_LOGGER = Logger.getLogger("org.apache.pdfbox");
    private static final Logger FONTBOX_LOGGER = Logger.getLogger("org.apache.fontbox");
    private static final Logger PARSER_LOGGER =
            Logger.getLogger("org.apache.pdfbox.pdfparser.synthetic-test");

    @Test
    void capturesParserWarningsByCategoryAndSeverityWithoutMessageParsing() {
        try (PdfBoxDiagnosticCapture capture = new PdfBoxDiagnosticCapture()) {
            PARSER_LOGGER.info("not a recovery");
            PARSER_LOGGER.warning("localized or version-specific recovery text");

            assertEquals(1, capture.warningCount());
        }
    }

    @Test
    void removesItsHandlerWhenClosed() {
        PdfBoxDiagnosticCapture capture = new PdfBoxDiagnosticCapture();
        capture.close();
        PARSER_LOGGER.warning("outside the operation scope");

        assertEquals(0, capture.warningCount());
    }

    @Test
    void suppressesThirdPartyConsolePropagationOnlyDuringTheAuditScope() {
        boolean originalPdfBox = PDFBOX_LOGGER.getUseParentHandlers();
        boolean originalFontBox = FONTBOX_LOGGER.getUseParentHandlers();

        try (PdfBoxDiagnosticCapture ignored = new PdfBoxDiagnosticCapture()) {
            assertFalse(PDFBOX_LOGGER.getUseParentHandlers());
            assertFalse(FONTBOX_LOGGER.getUseParentHandlers());
        }

        assertEquals(originalPdfBox, PDFBOX_LOGGER.getUseParentHandlers());
        assertEquals(originalFontBox, FONTBOX_LOGGER.getUseParentHandlers());
    }
}
