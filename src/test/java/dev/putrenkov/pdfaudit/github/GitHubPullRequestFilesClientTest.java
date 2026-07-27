package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubPullRequestFilesClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void retrievesEveryExpectedPageWithReadOnlyToken() throws IOException {
        List<HttpRequest> requests = new ArrayList<>();
        GitHubPullRequestFilesClient client = new GitHubPullRequestFilesClient(
                objectMapper,
                request -> {
                    requests.add(request);
                    int count = request.uri().getQuery().endsWith("page=1") ? 100 : 1;
                    return new GitHubPullRequestFilesClient.Response(
                            200,
                            responseBody(count, requests.size()));
                });

        List<PullRequestFile> files = client.listFiles(context(101), "secret-token");

        assertEquals(101, files.size());
        assertEquals(2, requests.size());
        assertEquals(
                "Bearer secret-token",
                requests.getFirst().headers().firstValue("Authorization").orElseThrow());
        assertTrue(requests.getLast().uri().getQuery().contains("page=2"));
    }

    @Test
    void rejectsIncompleteApiResponse() {
        GitHubPullRequestFilesClient client = new GitHubPullRequestFilesClient(
                objectMapper,
                request -> new GitHubPullRequestFilesClient.Response(
                        200,
                        responseBody(1, 1)));

        IOException exception = assertThrows(
                IOException.class,
                () -> client.listFiles(context(2), "token"));

        assertTrue(exception.getMessage().contains("expected 2"));
    }

    @Test
    void doesNotCallApiForEmptyPullRequest() throws IOException {
        GitHubPullRequestFilesClient client = new GitHubPullRequestFilesClient(
                objectMapper,
                request -> {
                    throw new AssertionError("API must not be called");
                });

        assertTrue(client.listFiles(context(0), "token").isEmpty());
    }

    private GitHubContext context(int changedFiles) {
        return new GitHubContext(
                "lMysticl/example",
                7,
                changedFiles,
                URI.create("https://api.github.test"),
                temporaryDirectory);
    }

    private String responseBody(int count, int page) {
        ArrayNode array = objectMapper.createArrayNode();
        for (int index = 0; index < count; index++) {
            array.addObject()
                    .put("filename", "docs/file-" + page + "-" + index + ".pdf")
                    .put("status", "modified");
        }
        return array.toString();
    }
}
