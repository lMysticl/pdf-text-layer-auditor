package dev.putrenkov.pdfaudit.github;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

final class ActionWorkloadPolicy {
    private ActionWorkloadPolicy() {
    }

    static void validate(
            List<ChangedPdfSelector.ChangedPdf> files,
            int maxPdfFiles,
            long maxTotalPdfBytes
    ) throws IOException {
        Objects.requireNonNull(files, "files");
        if (maxPdfFiles <= 0) {
            throw new IllegalArgumentException("maxPdfFiles must be positive");
        }
        if (maxTotalPdfBytes <= 0) {
            throw new IllegalArgumentException("maxTotalPdfBytes must be positive");
        }
        if (files.size() > maxPdfFiles) {
            throw new IllegalArgumentException(
                    "Pull request contains " + files.size()
                            + " changed PDF files; configured limit is " + maxPdfFiles);
        }

        long totalBytes = 0;
        for (ChangedPdfSelector.ChangedPdf file : files) {
            long fileBytes = Files.size(file.localPath());
            if (fileBytes > maxTotalPdfBytes - totalBytes) {
                throw new IllegalArgumentException(
                        "Changed PDFs have a combined size above the configured limit of "
                                + maxTotalPdfBytes + " bytes");
            }
            totalBytes += fileBytes;
        }
    }
}
