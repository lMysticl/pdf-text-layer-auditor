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
        assertEquals(
                PdfTextLayerAuditor.DEFAULT_MAX_FILE_SIZE_BYTES,
                options.maxFileSizeBytes());
        assertEquals(
                PdfTextLayerAuditor.DEFAULT_MAX_PAGE_COUNT,
                options.maxPageCount());
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
    void parsesConfiguredResourceLimits() {
        CliOptions options = CliOptions.parse(new String[] {
            "--max-pages", "25",
            "document.pdf",
            "--max-file-size-mib", "8"
        });

        assertEquals(25, options.maxPageCount());
        assertEquals(8L * 1024 * 1024, options.maxFileSizeBytes());
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
    void rejectsMissingAndInvalidResourceLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"document.pdf", "--max-pages"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--max-pages", "zero", "document.pdf"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--max-pages", "0", "document.pdf"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {
                    "--max-file-size-mib", "-1", "document.pdf"
                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {
                    "--max-file-size-mib", Long.toString(Long.MAX_VALUE), "document.pdf"
                }));
    }

    @Test
    void rejectsDuplicateResourceLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {
                    "--max-pages", "10", "--max-pages", "20", "document.pdf"
                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {
                    "--max-file-size-mib", "10",
                    "--max-file-size-mib", "20",
                    "document.pdf"
                }));
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
