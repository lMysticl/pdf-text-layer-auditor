package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionOptionsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void usesDocumentedDefaults() {
        ActionOptions options = ActionOptions.fromEnvironment(environment());

        assertTrue(options.failOnFindings());
        assertEquals(20, options.maxAnnotations());
        assertEquals(100L * 1024 * 1024, options.maxFileSizeBytes());
        assertEquals(1_000, options.maxPageCount());
        assertEquals(3.0f, options.tinyTextThresholdPoints());
        assertEquals(
                temporaryDirectory.resolve("pdf-text-layer-audit.json"),
                options.reportPath());
    }

    @Test
    void acceptsExplicitNonFailingPolicy() {
        Map<String, String> environment = environment();
        environment.put("INPUT_FAIL_ON_FINDINGS", "false");
        environment.put("INPUT_MAX_ANNOTATIONS", "0");
        environment.put("INPUT_TINY_TEXT_THRESHOLD_PT", "0");

        ActionOptions options = ActionOptions.fromEnvironment(environment);

        assertFalse(options.failOnFindings());
        assertEquals(0, options.maxAnnotations());
        assertEquals(0.0f, options.tinyTextThresholdPoints());
    }

    @Test
    void rejectsReportPathOutsideWorkspace() {
        Map<String, String> environment = environment();
        environment.put("INPUT_REPORT_PATH", "../report.json");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ActionOptions.fromEnvironment(environment));

        assertTrue(exception.getMessage().contains("escapes the workspace"));
    }

    @Test
    void rejectsInvalidPolicyValues() {
        Map<String, String> environment = environment();
        environment.put("INPUT_FAIL_ON_FINDINGS", "sometimes");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ActionOptions.fromEnvironment(environment));

        assertEquals("fail_on_findings must be true or false", exception.getMessage());
    }

    @Test
    void rejectsNonJsonReportTarget() {
        Map<String, String> environment = environment();
        environment.put("INPUT_REPORT_PATH", "README.md");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ActionOptions.fromEnvironment(environment));

        assertEquals("report_path must end with .json", exception.getMessage());
    }

    private Map<String, String> environment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("INPUT_TOKEN", "test-token");
        environment.put("GITHUB_WORKSPACE", temporaryDirectory.toString());
        return environment;
    }
}
