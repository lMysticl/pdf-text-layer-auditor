package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubActionEndToEndTest {
    @TempDir
    Path workspace;

    @Test
    void auditsChangedPdfAndPublishesReportSummaryAndOutputs() throws Exception {
        Path pdf = workspace.resolve("changed.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        Path event = workspace.resolve("event.json");
        Files.writeString(
                event,
                """
                {
                  "number": 42,
                  "pull_request": {
                    "changed_files": 1
                  }
                }
                """);
        Path output = workspace.resolve("github-output.txt");
        Path summary = workspace.resolve("github-summary.md");

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        server.createContext(
                "/repos/lMysticl/example/pulls/42/files",
                GitHubActionEndToEndTest::serveChangedFiles);
        server.start();

        try {
            Map<String, String> environment = new HashMap<>();
            environment.put("GITHUB_EVENT_NAME", "pull_request");
            environment.put("GITHUB_EVENT_PATH", event.toString());
            environment.put("GITHUB_REPOSITORY", "lMysticl/example");
            environment.put(
                    "GITHUB_API_URL",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            environment.put("GITHUB_WORKSPACE", workspace.toString());
            environment.put("GITHUB_OUTPUT", output.toString());
            environment.put("GITHUB_STEP_SUMMARY", summary.toString());
            environment.put("INPUT_TOKEN", "test-token");
            environment.put("INPUT_FAIL_ON_FINDINGS", "false");
            environment.put("INPUT_REPORT_PATH", "reports/audit.json");

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            int exitCode;
            try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                 PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
                exitCode = GitHubActionMain.run(Map.copyOf(environment), out, err);
            }

            assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
            assertTrue(
                    stdout.toString(StandardCharsets.UTF_8)
                            .contains("Audited 1 changed PDF file(s)"));
            assertTrue(Files.readString(output).contains("files_checked=1"));
            assertTrue(Files.readString(summary).contains("changed.pdf"));

            Path report = workspace.resolve("reports/audit.json");
            JsonNode reportJson = new ObjectMapper().readTree(report.toFile());
            assertEquals(1, reportJson.get("filesChecked").intValue());
            assertEquals("changed.pdf", reportJson.get("files").get(0).get("path").textValue());

            try (PDDocument ignored = Loader.loadPDF(pdf.toFile())) {
                assertEquals(1, ignored.getNumberOfPages());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsOversizedPullRequestWorkloadBeforePdfParsing() throws Exception {
        Files.write(workspace.resolve("one.pdf"), new byte[] {1});
        Files.write(workspace.resolve("two.pdf"), new byte[] {2});
        Path event = workspace.resolve("event.json");
        Files.writeString(
                event,
                """
                {
                  "number": 43,
                  "pull_request": {
                    "changed_files": 2
                  }
                }
                """);

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        server.createContext(
                "/repos/lMysticl/example/pulls/43/files",
                exchange -> serveChangedFiles(exchange, "one.pdf", "two.pdf"));
        server.start();

        try {
            Map<String, String> environment = new HashMap<>();
            environment.put("GITHUB_EVENT_NAME", "pull_request");
            environment.put("GITHUB_EVENT_PATH", event.toString());
            environment.put("GITHUB_REPOSITORY", "lMysticl/example");
            environment.put(
                    "GITHUB_API_URL",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            environment.put("GITHUB_WORKSPACE", workspace.toString());
            environment.put("INPUT_TOKEN", "test-token");
            environment.put("INPUT_MAX_FILES", "1");

            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            int exitCode;
            try (PrintStream out = new PrintStream(new ByteArrayOutputStream());
                 PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
                exitCode = GitHubActionMain.run(Map.copyOf(environment), out, err);
            }

            assertEquals(2, exitCode);
            assertTrue(
                    stderr.toString(StandardCharsets.UTF_8)
                            .contains("2 changed PDF files; configured limit is 1"));
            assertFalse(Files.exists(workspace.resolve("pdf-text-layer-audit.json")));
        } finally {
            server.stop(0);
        }
    }

    private static void serveChangedFiles(HttpExchange exchange) throws IOException {
        serveChangedFiles(exchange, "changed.pdf");
    }

    private static void serveChangedFiles(
            HttpExchange exchange,
            String... filenames
    ) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < filenames.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append("""
                    {
                      "filename": "%s",
                      "status": "added"
                    }
                    """.formatted(filenames[index]));
        }
        json.append(']');
        byte[] body = """
                %s
                """.formatted(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(
                "Content-Type",
                "application/vnd.github+json");
        exchange.sendResponseHeaders(200, body.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        }
    }
}
