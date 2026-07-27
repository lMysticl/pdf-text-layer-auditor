package dev.putrenkov.pdfaudit.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

record GitHubContext(
        String repository,
        int pullRequestNumber,
        int changedFileCount,
        URI apiUrl,
        Path workspace
) {
    private static final long MAX_EVENT_BYTES = 1024L * 1024;
    private static final int MAX_PULL_REQUEST_FILES = 3_000;
    private static final Pattern REPOSITORY =
            Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");

    static GitHubContext fromEnvironment(
            Map<String, String> environment,
            ObjectMapper objectMapper
    ) throws IOException {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(objectMapper, "objectMapper");

        String eventName = required(environment, "GITHUB_EVENT_NAME");
        if (!"pull_request".equals(eventName)) {
            throw new IllegalArgumentException(
                    "This action supports the pull_request event only");
        }

        String repository = required(environment, "GITHUB_REPOSITORY");
        if (!REPOSITORY.matcher(repository).matches()) {
            throw new IllegalArgumentException("Invalid GITHUB_REPOSITORY value");
        }

        Path eventPath = Path.of(required(environment, "GITHUB_EVENT_PATH"));
        if (!Files.isRegularFile(eventPath) || Files.size(eventPath) > MAX_EVENT_BYTES) {
            throw new IllegalArgumentException(
                    "GITHUB_EVENT_PATH must be a regular JSON file no larger than 1 MiB");
        }
        JsonNode event = objectMapper.readTree(eventPath.toFile());
        int pullRequestNumber = requiredPositiveInt(event.get("number"), "pull request number");
        JsonNode pullRequest = event.get("pull_request");
        if (pullRequest == null || !pullRequest.isObject()) {
            throw new IllegalArgumentException(
                    "GitHub event does not contain a pull_request object");
        }
        int changedFiles = requiredNonNegativeInt(
                pullRequest.get("changed_files"),
                "pull_request.changed_files");
        if (changedFiles > MAX_PULL_REQUEST_FILES) {
            throw new IllegalArgumentException(
                    "Pull requests with more than 3000 changed files are not supported");
        }

        URI apiUrl = URI.create(
                environment.getOrDefault("GITHUB_API_URL", "https://api.github.com"));
        if (apiUrl.getHost() == null
                || (!"https".equalsIgnoreCase(apiUrl.getScheme())
                && !"http".equalsIgnoreCase(apiUrl.getScheme()))) {
            throw new IllegalArgumentException("GITHUB_API_URL must be an HTTP(S) URL");
        }

        Path workspace = Path.of(required(environment, "GITHUB_WORKSPACE"))
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(workspace)) {
            throw new IllegalArgumentException("GITHUB_WORKSPACE must be a directory");
        }

        return new GitHubContext(
                repository,
                pullRequestNumber,
                changedFiles,
                apiUrl,
                workspace);
    }

    private static int requiredPositiveInt(JsonNode value, String name) {
        int parsed = requiredNonNegativeInt(value, name);
        if (parsed == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private static int requiredNonNegativeInt(JsonNode value, String name) {
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        int parsed = value.intValue();
        if (parsed < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return parsed;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required environment value: " + name);
        }
        return value;
    }
}
