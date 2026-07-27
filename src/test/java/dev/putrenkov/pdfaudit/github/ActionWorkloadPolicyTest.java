package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionWorkloadPolicyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsBatchWithinFileAndByteLimits() throws IOException {
        List<ChangedPdfSelector.ChangedPdf> files = List.of(
                changedPdf("one.pdf", 4),
                changedPdf("two.pdf", 5));

        assertDoesNotThrow(() -> ActionWorkloadPolicy.validate(files, 2, 9));
    }

    @Test
    void rejectsMorePdfsThanConfigured() throws IOException {
        List<ChangedPdfSelector.ChangedPdf> files = List.of(
                changedPdf("one.pdf", 1),
                changedPdf("two.pdf", 1));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ActionWorkloadPolicy.validate(files, 1, 10));

        assertTrue(exception.getMessage().contains("2 changed PDF files"));
        assertTrue(exception.getMessage().contains("limit is 1"));
    }

    @Test
    void rejectsAggregatePdfBytesAboveConfiguredLimit() throws IOException {
        List<ChangedPdfSelector.ChangedPdf> files = List.of(
                changedPdf("one.pdf", 4),
                changedPdf("two.pdf", 5));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ActionWorkloadPolicy.validate(files, 2, 8));

        assertTrue(exception.getMessage().contains("combined size"));
        assertTrue(exception.getMessage().contains("limit of 8 bytes"));
    }

    private ChangedPdfSelector.ChangedPdf changedPdf(String name, int size) throws IOException {
        Path path = Files.write(temporaryDirectory.resolve(name), new byte[size]);
        return new ChangedPdfSelector.ChangedPdf(name, path);
    }
}
