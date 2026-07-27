package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangedPdfSelectorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void selectsAddedModifiedAndRenamedPdfsOnly() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("docs"));
        Files.write(temporaryDirectory.resolve("docs/one.pdf"), new byte[] {1});
        Files.write(temporaryDirectory.resolve("docs/TWO.PDF"), new byte[] {2});

        List<ChangedPdfSelector.ChangedPdf> selected = new ChangedPdfSelector().select(
                List.of(
                        new PullRequestFile("docs/one.pdf", "added"),
                        new PullRequestFile("docs/TWO.PDF", "renamed"),
                        new PullRequestFile("docs/deleted.pdf", "removed"),
                        new PullRequestFile("README.md", "modified"),
                        new PullRequestFile("docs/one.pdf", "modified")),
                temporaryDirectory);

        assertEquals(2, selected.size());
        assertEquals("docs/one.pdf", selected.getFirst().repositoryPath());
        assertEquals("docs/TWO.PDF", selected.getLast().repositoryPath());
    }

    @Test
    void rejectsPathTraversal() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ChangedPdfSelector().select(
                        List.of(new PullRequestFile("../outside.pdf", "added")),
                        temporaryDirectory));

        assertTrue(exception.getMessage().contains("escapes the workspace"));
    }
}
