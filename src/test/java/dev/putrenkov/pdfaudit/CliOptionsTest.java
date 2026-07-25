package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CliOptionsTest {
    @Test
    void parsesTextAudit() {
        CliOptions options = CliOptions.parse(new String[] {"document.pdf"});

        assertEquals(CliOptions.Mode.AUDIT, options.mode());
        assertEquals(Path.of("document.pdf"), options.input());
        assertEquals(CliOptions.OutputFormat.TEXT, options.outputFormat());
    }

    @Test
    void parsesJsonBeforeOrAfterFile() {
        assertEquals(
                CliOptions.OutputFormat.JSON,
                CliOptions.parse(new String[] {"--json", "document.pdf"}).outputFormat());
        assertEquals(
                CliOptions.OutputFormat.JSON,
                CliOptions.parse(new String[] {"document.pdf", "--json"}).outputFormat());
    }

    @Test
    void parsesStandaloneActions() {
        assertEquals(
                CliOptions.Mode.HELP,
                CliOptions.parse(new String[] {"--help"}).mode());
        assertEquals(
                CliOptions.Mode.HELP,
                CliOptions.parse(new String[] {"-h"}).mode());
        assertEquals(
                CliOptions.Mode.VERSION,
                CliOptions.parse(new String[] {"--version"}).mode());
    }

    @Test
    void rejectsMissingFile() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--json"}));

        assertEquals("A PDF file is required", exception.getMessage());
    }

    @Test
    void rejectsUnknownOption() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--yaml", "document.pdf"}));

        assertEquals("Unknown option: --yaml", exception.getMessage());
    }

    @Test
    void rejectsDuplicateOption() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--json", "--json", "document.pdf"}));

        assertEquals("--json may only be specified once", exception.getMessage());
    }

    @Test
    void rejectsMultipleFilesAndCombinedStandaloneAction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"first.pdf", "second.pdf"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--help", "document.pdf"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--version", "document.pdf"}));
    }
}
