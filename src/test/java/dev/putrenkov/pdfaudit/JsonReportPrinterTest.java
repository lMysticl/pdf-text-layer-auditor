package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonReportPrinterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void serializesCompleteVersionFourReportAndEscapesStrings() throws Exception {
        PageAudit page = new PageAudit(
                1,
                4,
                3,
                1,
                0,
                2,
                List.of(new FontAudit("A\"B\\C\n\u0001\uD83D\uDE00", true, false, 4)),
                List.of(Finding.MISSING_UNICODE));
        AuditReport report = new AuditReport(
                Path.of("document.pdf"),
                123,
                1,
                false,
                true,
                3.0f,
                List.of(page));

        String json = JsonReportPrinter.toJson(report);
        var root = MAPPER.readTree(json);

        assertEquals(4, root.path("schemaVersion").asInt());
        assertEquals("A\"B\\C\n\u0001\uD83D\uDE00",
                root.path("pages").path(0).path("fonts").path(0).path("name").asText());
        assertTrue(root.path("parseHealth").path("complete").asBoolean());
        assertEquals(0, root.path("parseHealth").path("parserWarningCount").asInt());
        assertFalse(root.path("completeness").path("geometryVisibility").asBoolean());
        assertTrue(root.path("pages").path(0).path("geometryVisibility")
                .path("invisibleGlyphCount").isNull());
        assertFalse(root.path("pages").path(0).path("spatialEvidence")
                .path("assessed").asBoolean());
        assertTrue(root.path("pages").path(0).path("spatialEvidence")
                .path("coordinateSpace").isNull());
        assertEquals(0, root.path("pages").path(0).path("spatialEvidence")
                .path("visualRegions").path("totalRegionCount").asInt());

        assertValidAgainst("report-schema-v4.json", json);
    }

    @Test
    void serializesEmptyCollections() throws Exception {
        AuditReport report = new AuditReport(
                Path.of("document.pdf"),
                0,
                0,
                false,
                true,
                0.0f,
                List.of());

        var root = MAPPER.readTree(JsonReportPrinter.toJson(report));

        assertEquals(4, root.path("schemaVersion").asInt());
        assertEquals(0, root.path("pages").size());
        assertEquals(0, root.path("parseHealth").path("diagnostics").size());
    }

    @Test
    void versionOneSchemaRemainsPublishedForExistingConsumers() throws Exception {
        String legacyJson = """
                {"schemaVersion":1,"file":"document.pdf","fileSizeBytes":123,
                "pageCount":1,"inspectedPageCount":0,"encrypted":true,
                "extractionAllowed":false,"tinyTextThresholdPoints":3.0,
                "needsAttention":false,"pagesNeedingAttention":0,"pages":[]}
                """;

        assertValidAgainst("report-schema-v1.json", legacyJson);
    }

    private static void assertValidAgainst(String schemaName, String json) throws Exception {
        String schemaDocument = Files.readString(Path.of("docs", schemaName));
        SchemaRegistry registry =
                SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

        var errors = registry.getSchema(schemaDocument).validate(json, InputFormat.JSON);

        assertTrue(errors.isEmpty(), errors::toString);
    }
}
