package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubActionRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void findingsFailOnlyWhenPolicyRequiresIt() throws IOException {
        Path pdf = Files.write(temporaryDirectory.resolve("document.pdf"), new byte[] {1});
        ChangedPdfSelector.ChangedPdf changed =
                new ChangedPdfSelector.ChangedPdf("document.pdf", pdf);

        GitHubActionRunner runner = new GitHubActionRunner(
                path -> ActionTestReports.withNoTextLayer(path));

        assertEquals(1, runner.run(List.of(changed), true).exitCode());
        assertEquals(0, runner.run(List.of(changed), false).exitCode());
    }

    @Test
    void auditErrorTakesPrecedenceOverFindingPolicy() throws IOException {
        Path pdf = Files.write(temporaryDirectory.resolve("broken.pdf"), new byte[] {1});
        ChangedPdfSelector.ChangedPdf changed =
                new ChangedPdfSelector.ChangedPdf("broken.pdf", pdf);
        GitHubActionRunner runner = new GitHubActionRunner(
                path -> {
                    throw new IOException("cannot read\nuntrusted detail");
                });

        ActionRun run = runner.run(List.of(changed), false);

        assertEquals(2, run.exitCode());
        assertEquals("cannot read untrusted detail", run.files().getFirst().error());
    }
}
