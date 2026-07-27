package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubContextTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsPullRequestIdentityAndLimits() throws IOException {
        GitHubContext context = GitHubContext.fromEnvironment(
                environment(42, 17),
                objectMapper);

        assertEquals("lMysticl/example", context.repository());
        assertEquals(42, context.pullRequestNumber());
        assertEquals(17, context.changedFileCount());
        assertEquals("https://api.github.com", context.apiUrl().toString());
        assertEquals(temporaryDirectory, context.workspace());
    }

    @Test
    void rejectsPullRequestTarget() throws IOException {
        Map<String, String> environment = environment(42, 1);
        environment.put("GITHUB_EVENT_NAME", "pull_request_target");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GitHubContext.fromEnvironment(environment, objectMapper));

        assertTrue(exception.getMessage().contains("pull_request event only"));
    }

    @Test
    void rejectsApiLimitOverflow() throws IOException {
        Map<String, String> environment = environment(42, 3_001);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GitHubContext.fromEnvironment(environment, objectMapper));

        assertTrue(exception.getMessage().contains("more than 3000"));
    }

    private Map<String, String> environment(int number, int changedFiles) throws IOException {
        Path event = temporaryDirectory.resolve("event-" + changedFiles + ".json");
        Files.writeString(
                event,
                """
                {
                  "number": %d,
                  "pull_request": {
                    "changed_files": %d
                  }
                }
                """.formatted(number, changedFiles));

        Map<String, String> environment = new HashMap<>();
        environment.put("GITHUB_EVENT_NAME", "pull_request");
        environment.put("GITHUB_EVENT_PATH", event.toString());
        environment.put("GITHUB_REPOSITORY", "lMysticl/example");
        environment.put("GITHUB_WORKSPACE", temporaryDirectory.toString());
        return environment;
    }
}
