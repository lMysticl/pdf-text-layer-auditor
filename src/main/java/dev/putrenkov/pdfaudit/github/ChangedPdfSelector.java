package dev.putrenkov.pdfaudit.github;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ChangedPdfSelector {
    List<ChangedPdf> select(List<PullRequestFile> files, Path workspace) throws IOException {
        List<ChangedPdf> selected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PullRequestFile file : files) {
            if ("removed".equals(file.status())
                    || !file.filename().toLowerCase(Locale.ROOT).endsWith(".pdf")
                    || !seen.add(file.filename())) {
                continue;
            }
            selected.add(new ChangedPdf(
                    file.filename(),
                    WorkspacePaths.resolveExistingFile(workspace, file.filename())));
        }
        return List.copyOf(selected);
    }

    record ChangedPdf(String repositoryPath, Path localPath) {
    }
}
