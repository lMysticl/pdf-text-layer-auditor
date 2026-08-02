package dev.putrenkov.pdfaudit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionReportWriterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesCombinedVersionedReportWithoutAbsoluteInputPaths() throws IOException {
        Path localPdf = temporaryDirectory.resolve("document.pdf").toAbsolutePath();
        ActionRun run = new ActionRun(
                List.of(ActionRun.AuditedPdf.success(
                        "docs/document.pdf",
                        ActionTestReports.withNoTextLayer(localPdf))),
                true);
        GitHubContext context = new GitHubContext(
                "lMysticl/example",
                12,
                1,
                URI.create("https://api.github.com"),
                temporaryDirectory);
        Path output = temporaryDirectory.resolve("reports/result.json");

        new ActionReportWriter(objectMapper).write(run, context, output);

        JsonNode report = objectMapper.readTree(output.toFile());
        assertEquals(3, report.path("schemaVersion").intValue());
        assertEquals("findings", report.path("conclusion").textValue());
        assertEquals("docs/document.pdf", report.at("/files/0/path").textValue());
        assertEquals("docs/document.pdf", report.at("/files/0/report/file").textValue());
        assertEquals(
                "NO_TEXT_LAYER",
                report.at("/files/0/report/pages/0/findings/0/code").textValue());
        if (Files.getFileStore(output).supportsFileAttributeView("posix")) {
            var permissions = Files.getPosixFilePermissions(output);
            assertTrue(permissions.contains(PosixFilePermission.GROUP_READ));
            assertTrue(permissions.contains(PosixFilePermission.OTHERS_READ));
        }

        String actionSchema = Files.readString(
                Path.of("docs", "action-report-schema-v3.json"));
        String auditorSchema = Files.readString(
                Path.of("docs", "report-schema-v3.json"));
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(Map.of(
                        "https://raw.githubusercontent.com/lMysticl/pdf-text-layer-auditor/main/docs/report-schema-v3.json",
                        auditorSchema)));

        var errors = registry.getSchema(actionSchema)
                .validate(Files.readString(output), InputFormat.JSON);

        assertEquals(List.of(), errors);
    }
}
