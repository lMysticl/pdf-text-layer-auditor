package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonReportPrinterTest {
    @Test
    void serializesCompleteReportAndEscapesStrings() throws Exception {
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

        assertEquals(
                "{\"schemaVersion\":1,\"file\":\"document.pdf\","
                        + "\"fileSizeBytes\":123,\"pageCount\":1,"
                        + "\"inspectedPageCount\":1,"
                        + "\"encrypted\":false,\"extractionAllowed\":true,"
                        + "\"tinyTextThresholdPoints\":3.0,"
                        + "\"needsAttention\":true,\"pagesNeedingAttention\":1,"
                        + "\"pages\":[{\"pageNumber\":1,\"glyphCount\":4,"
                        + "\"unicodeCharacterCount\":3,\"missingUnicodeGlyphCount\":1,"
                        + "\"replacementCharacterCount\":0,\"tinyTextGlyphCount\":2,"
                        + "\"needsAttention\":true,\"fonts\":[{\"name\":\"A\\\"B\\\\C\\n"
                        + "\\u0001\\ud83d\\ude00\","
                        + "\"embedded\":true,\"damaged\":false,\"glyphCount\":4}],"
                        + "\"findings\":[{\"code\":\"MISSING_UNICODE\","
                        + "\"description\":\"Some glyphs have no usable Unicode mapping.\"}]}]}",
                json);

        String schemaDocument = Files.readString(Path.of("docs", "report-schema-v1.json"));
        SchemaRegistry registry =
                SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

        var errors = registry.getSchema(schemaDocument).validate(json, InputFormat.JSON);

        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    void serializesEmptyCollections() {
        AuditReport report = new AuditReport(
                Path.of("document.pdf"),
                0,
                0,
                false,
                true,
                0.0f,
                List.of());

        assertEquals(
                "{\"schemaVersion\":1,\"file\":\"document.pdf\","
                        + "\"fileSizeBytes\":0,\"pageCount\":0,"
                        + "\"inspectedPageCount\":0,"
                        + "\"encrypted\":false,\"extractionAllowed\":true,"
                        + "\"tinyTextThresholdPoints\":0.0,"
                        + "\"needsAttention\":false,\"pagesNeedingAttention\":0,\"pages\":[]}",
                JsonReportPrinter.toJson(report));
    }

    @Test
    void versionOneSchemaRemainsCompatibleWithExtractionDeniedReports() throws Exception {
        String schemaDocument = Files.readString(Path.of("docs", "report-schema-v1.json"));
        SchemaRegistry registry =
                SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        AuditReport report = new AuditReport(
                Path.of("document.pdf"),
                123,
                1,
                true,
                false,
                3.0f,
                List.of());
        String json = JsonReportPrinter.toJson(report);

        var errors = registry.getSchema(schemaDocument).validate(json, InputFormat.JSON);

        assertTrue(errors.isEmpty(), errors::toString);
    }
}
