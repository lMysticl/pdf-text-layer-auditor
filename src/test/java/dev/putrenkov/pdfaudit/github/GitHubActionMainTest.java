package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GitHubActionMainTest {
    @Test
    void mapsDockerArgumentsToActionInputs() {
        Map<String, String> environment = GitHubActionMain.environmentWithInputs(
                Map.of("GITHUB_REPOSITORY", "lMysticl/example"),
                new String[] {
                    "token", "false", "5", "25", "750", "50", "500", "2.5", "report.json"
                });

        assertEquals("token", environment.get("INPUT_TOKEN"));
        assertEquals("false", environment.get("INPUT_FAIL_ON_FINDINGS"));
        assertEquals("5", environment.get("INPUT_MAX_ANNOTATIONS"));
        assertEquals("25", environment.get("INPUT_MAX_FILES"));
        assertEquals("750", environment.get("INPUT_MAX_TOTAL_SIZE_MIB"));
        assertEquals("50", environment.get("INPUT_MAX_FILE_SIZE_MIB"));
        assertEquals("500", environment.get("INPUT_MAX_PAGES"));
        assertEquals("2.5", environment.get("INPUT_TINY_TEXT_THRESHOLD_PT"));
        assertEquals("report.json", environment.get("INPUT_REPORT_PATH"));
        assertEquals("lMysticl/example", environment.get("GITHUB_REPOSITORY"));
    }

    @Test
    void rejectsPartialDockerArgumentList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GitHubActionMain.environmentWithInputs(
                        Map.of(),
                        new String[] {"token"}));
    }
}
