package dev.putrenkov.pdfaudit.github;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

final class WorkspacePaths {
    private WorkspacePaths() {
    }

    static Path resolveExistingFile(Path workspace, String repositoryPath) throws IOException {
        Path target = resolve(workspace, repositoryPath);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(
                    "Changed PDF is missing or is not a regular file: " + repositoryPath);
        }

        Path realWorkspace = workspace.toRealPath();
        Path realTarget = target.toRealPath();
        if (!realTarget.startsWith(realWorkspace)) {
            throw new IllegalArgumentException(
                    "Changed PDF resolves outside the workspace: " + repositoryPath);
        }
        return realTarget;
    }

    static Path resolveOutput(Path workspace, String repositoryPath) {
        if (repositoryPath == null
                || repositoryPath.isBlank()
                || repositoryPath.indexOf('\r') >= 0
                || repositoryPath.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    "report_path must be a non-empty workspace-relative path");
        }
        if (!repositoryPath.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("report_path must end with .json");
        }
        return resolve(workspace, repositoryPath);
    }

    static void prepareOutputParent(Path workspace, Path output) throws IOException {
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Report path must have a parent directory");
        }

        Path relativeParent = normalizedWorkspace.relativize(parent);
        Path cursor = normalizedWorkspace;
        for (Path segment : relativeParent) {
            cursor = cursor.resolve(segment);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(cursor)) {
                throw new IllegalArgumentException(
                        "Report path must not traverse a symbolic link");
            }
        }

        Files.createDirectories(parent);
        Path realWorkspace = normalizedWorkspace.toRealPath();
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(realWorkspace)) {
            throw new IllegalArgumentException("Report path resolves outside the workspace");
        }
    }

    private static Path resolve(Path workspace, String repositoryPath) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            throw new IllegalArgumentException("Repository path must not be blank");
        }
        Path relative = Path.of(repositoryPath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Repository path must be relative: " + repositoryPath);
        }

        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        Path target = normalizedWorkspace.resolve(relative).normalize();
        if (!target.startsWith(normalizedWorkspace)) {
            throw new IllegalArgumentException(
                    "Repository path escapes the workspace: " + repositoryPath);
        }
        return target;
    }
}
