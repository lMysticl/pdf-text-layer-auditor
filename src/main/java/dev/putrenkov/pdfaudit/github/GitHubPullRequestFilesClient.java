package dev.putrenkov.pdfaudit.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class GitHubPullRequestFilesClient {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    private static final String API_VERSION = "2026-03-10";

    private final ObjectMapper objectMapper;
    private final Sender sender;

    GitHubPullRequestFilesClient(ObjectMapper objectMapper, Sender sender) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    static GitHubPullRequestFilesClient create(ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new GitHubPullRequestFilesClient(
                objectMapper,
                request -> {
                    try {
                        HttpResponse<InputStream> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofInputStream());
                        try (InputStream body = response.body()) {
                            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                            if (bytes.length > MAX_RESPONSE_BYTES) {
                                throw new IOException(
                                        "GitHub API response exceeds the 5 MiB limit");
                            }
                            return new Response(
                                    response.statusCode(),
                                    new String(bytes, StandardCharsets.UTF_8));
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("GitHub API request was interrupted", exception);
                    }
                });
    }

    List<PullRequestFile> listFiles(GitHubContext context, String token) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(token, "token");
        if (context.changedFileCount() == 0) {
            return List.of();
        }

        int pageCount = (context.changedFileCount() + PAGE_SIZE - 1) / PAGE_SIZE;
        List<PullRequestFile> files = new ArrayList<>(context.changedFileCount());
        for (int page = 1; page <= pageCount; page++) {
            Response response = sender.send(request(context, token, page));
            if (response.statusCode() != 200) {
                throw new IOException(
                        "GitHub API returned HTTP " + response.statusCode()
                                + " while listing pull request files");
            }

            JsonNode body = objectMapper.readTree(response.body());
            if (!body.isArray()) {
                throw new IOException(
                        "GitHub API returned an invalid pull request files response");
            }
            for (JsonNode item : body) {
                JsonNode filename = item.get("filename");
                JsonNode status = item.get("status");
                if (filename == null
                        || !filename.isTextual()
                        || filename.textValue().isBlank()
                        || status == null
                        || !status.isTextual()
                        || status.textValue().isBlank()) {
                    throw new IOException(
                            "GitHub API returned an invalid pull request file entry");
                }
                files.add(new PullRequestFile(filename.textValue(), status.textValue()));
            }
        }

        if (files.size() != context.changedFileCount()) {
            throw new IOException(
                    "GitHub API returned " + files.size() + " pull request files; expected "
                            + context.changedFileCount());
        }
        return List.copyOf(files);
    }

    private static HttpRequest request(GitHubContext context, String token, int page) {
        String baseUrl = context.apiUrl().toString().replaceFirst("/+$", "");
        URI uri = URI.create(
                baseUrl
                        + "/repos/"
                        + context.repository()
                        + "/pulls/"
                        + context.pullRequestNumber()
                        + "/files?per_page="
                        + PAGE_SIZE
                        + "&page="
                        + page);
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "pdf-text-layer-auditor-action")
                .GET()
                .build();
    }

    @FunctionalInterface
    interface Sender {
        Response send(HttpRequest request) throws IOException;
    }

    record Response(int statusCode, String body) {
    }
}
