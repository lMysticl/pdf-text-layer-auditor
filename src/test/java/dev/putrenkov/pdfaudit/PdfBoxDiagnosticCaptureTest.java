package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class PdfBoxDiagnosticCaptureTest {
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
}
