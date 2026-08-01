package dev.putrenkov.pdfaudit;

import java.io.PrintStream;
import java.util.Objects;

public final class JsonReportPrinter {
    public static final int SCHEMA_VERSION = 2;

    public void print(AuditReport report, PrintStream out) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(out, "out");
        out.println(toJson(report));
    }

    public static String toJson(AuditReport report) {
        StringBuilder json = new StringBuilder(1_024);
        json.append('{');
        appendNumberProperty(json, "schemaVersion", SCHEMA_VERSION);
        appendStringProperty(json, "file", report.file().toString());
        appendNumberProperty(json, "fileSizeBytes", report.fileSizeBytes());
        appendNumberProperty(json, "pageCount", report.pageCount());
        appendNumberProperty(json, "inspectedPageCount", report.pages().size());
        appendBooleanProperty(json, "encrypted", report.encrypted());
        appendBooleanProperty(json, "extractionAllowed", report.extractionAllowed());
        appendDecimalProperty(
                json,
                "tinyTextThresholdPoints",
                report.tinyTextThresholdPoints());
        appendParseHealth(json, report.parseHealth());
        appendCompleteness(json, report.completeness());
        appendBooleanProperty(json, "needsAttention", report.needsAttention());
        appendNumberProperty(json, "pagesNeedingAttention", report.pagesNeedingAttention());
        json.append(",\"pages\":[");

        for (int index = 0; index < report.pages().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendPage(json, report.pages().get(index));
        }

        return json.append("]}").toString();
    }

    private static void appendParseHealth(StringBuilder json, ParseHealth health) {
        appendPropertyPrefix(json, "parseHealth");
        json.append('{');
        appendBooleanProperty(json, "complete", health.complete());
        appendBooleanProperty(json, "recovered", health.recovered());
        json.append(",\"diagnostics\":[");
        for (int index = 0; index < health.diagnostics().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            ParseDiagnostic diagnostic = health.diagnostics().get(index);
            json.append('{');
            appendStringProperty(json, "code", diagnostic.code().name());
            appendNumberProperty(json, "pageNumber", diagnostic.pageNumber());
            appendStringProperty(json, "fontName", diagnostic.fontName());
            json.append('}');
        }
        json.append("]}");
    }

    private static void appendCompleteness(
            StringBuilder json,
            EvidenceCompleteness completeness
    ) {
        appendPropertyPrefix(json, "completeness");
        json.append('{');
        appendBooleanProperty(json, "pageContent", completeness.pageContent());
        appendBooleanProperty(json, "formXObjects", completeness.formXObjects());
        appendBooleanProperty(json, "semanticMappings", completeness.semanticMappings());
        appendBooleanProperty(json, "readingOrder", completeness.readingOrder());
        appendBooleanProperty(json, "geometryVisibility", completeness.geometryVisibility());
        appendBooleanProperty(json, "annotations", completeness.annotations());
        appendBooleanProperty(json, "optionalContent", completeness.optionalContent());
        json.append('}');
    }

    private static void appendPage(StringBuilder json, PageAudit page) {
        json.append('{');
        appendNumberProperty(json, "pageNumber", page.pageNumber());
        appendNumberProperty(json, "glyphCount", page.glyphCount());
        appendNumberProperty(json, "unicodeCharacterCount", page.unicodeCharacterCount());
        appendNumberProperty(json, "missingUnicodeGlyphCount", page.missingUnicodeGlyphCount());
        appendNumberProperty(json, "replacementCharacterCount", page.replacementCharacterCount());
        appendNumberProperty(json, "tinyTextGlyphCount", page.tinyTextGlyphCount());
        appendStringProperty(json, "classification", page.classification().name());
        appendTextSurfaces(json, page.textSurfaces());
        appendSemanticMapping(json, page.semanticMapping());
        appendReadingOrder(json, page.readingOrder());
        appendGeometryVisibility(json, page.geometryVisibility());
        appendVisualContent(json, page.visualContent());
        appendBooleanProperty(json, "needsAttention", page.needsAttention());
        json.append(",\"fonts\":[");

        for (int index = 0; index < page.fonts().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendFont(json, page.fonts().get(index));
        }

        json.append("],\"findings\":[");
        for (int index = 0; index < page.findings().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendFinding(json, page.findings().get(index));
        }
        json.append("]}");
    }

    private static void appendTextSurfaces(StringBuilder json, TextSurfaceAudit surfaces) {
        appendPropertyPrefix(json, "textSurfaces");
        json.append('{');
        appendNumberProperty(json, "pageContentGlyphCount", surfaces.pageContentGlyphCount());
        appendNumberProperty(json, "formXObjectGlyphCount", surfaces.formXObjectGlyphCount());
        appendNumberProperty(json, "actualTextGlyphCount", surfaces.actualTextGlyphCount());
        appendNumberProperty(
                json,
                "actualTextCharacterCount",
                surfaces.actualTextCharacterCount());
        json.append('}');
    }

    private static void appendSemanticMapping(
            StringBuilder json,
            SemanticMappingAudit mapping
    ) {
        appendPropertyPrefix(json, "semanticMapping");
        json.append('{');
        appendNumberProperty(json, "rawMappedGlyphCount", mapping.rawMappedGlyphCount());
        appendNumberProperty(json, "rawUnmappedGlyphCount", mapping.rawUnmappedGlyphCount());
        appendNumberProperty(
                json,
                "actualTextResolvedGlyphCount",
                mapping.actualTextResolvedGlyphCount());
        appendNumberProperty(
                json,
                "malformedToUnicodeFontCount",
                mapping.malformedToUnicodeFontCount());
        json.append('}');
    }

    private static void appendReadingOrder(StringBuilder json, ReadingOrderAudit order) {
        appendPropertyPrefix(json, "readingOrder");
        json.append('{');
        appendBooleanProperty(json, "assessed", order.assessed());
        appendBooleanProperty(json, "diverges", order.diverges());
        appendNumberProperty(json, "streamCharacterCount", order.streamCharacterCount());
        appendNumberProperty(json, "positionCharacterCount", order.positionCharacterCount());
        json.append('}');
    }

    private static void appendGeometryVisibility(
            StringBuilder json,
            GeometryVisibilityAudit geometry
    ) {
        appendPropertyPrefix(json, "geometryVisibility");
        json.append('{');
        appendBooleanProperty(json, "assessed", geometry.assessed());
        appendNullableNumberProperty(json, "visibleGlyphCount", geometry.visibleGlyphCount());
        appendNullableNumberProperty(json, "invisibleGlyphCount", geometry.invisibleGlyphCount());
        appendNullableNumberProperty(json, "offPageGlyphCount", geometry.offPageGlyphCount());
        appendNullableNumberProperty(json, "clippedGlyphCount", geometry.clippedGlyphCount());
        appendNullableNumberProperty(
                json,
                "transparentGlyphCount",
                geometry.transparentGlyphCount());
        appendNullableNumberProperty(
                json,
                "duplicateOverlapGlyphCount",
                geometry.duplicateOverlapGlyphCount());
        appendNullableNumberProperty(json, "rotatedGlyphCount", geometry.rotatedGlyphCount());
        appendNullableNumberProperty(json, "verticalGlyphCount", geometry.verticalGlyphCount());
        json.append('}');
    }

    private static void appendVisualContent(
            StringBuilder json,
            VisualContentAudit visual
    ) {
        appendPropertyPrefix(json, "visualContent");
        json.append('{');
        appendBooleanProperty(json, "assessed", visual.assessed());
        appendNullableNumberProperty(json, "imageCount", visual.imageCount());
        appendNullableDecimalProperty(
                json,
                "maxImageCoverageRatio",
                visual.maxImageCoverageRatio());
        appendNullableDecimalProperty(
                json,
                "combinedImageCoverageRatio",
                visual.combinedImageCoverageRatio());
        appendNullableNumberProperty(
                json,
                "paintedVectorPathCount",
                visual.paintedVectorPathCount());
        appendNullableNumberProperty(json, "annotationCount", visual.annotationCount());
        appendNullableNumberProperty(
                json,
                "widgetAnnotationCount",
                visual.widgetAnnotationCount());
        appendNullableBooleanProperty(
                json,
                "optionalContentPresent",
                visual.optionalContentPresent());
        json.append('}');
    }

    private static void appendFont(StringBuilder json, FontAudit font) {
        json.append('{');
        appendStringProperty(json, "name", font.name());
        appendBooleanProperty(json, "embedded", font.embedded());
        appendBooleanProperty(json, "damaged", font.damaged());
        appendNumberProperty(json, "glyphCount", font.glyphCount());
        json.append('}');
    }

    private static void appendFinding(StringBuilder json, Finding finding) {
        json.append('{');
        appendStringProperty(json, "code", finding.name());
        appendStringProperty(json, "description", finding.description());
        json.append('}');
    }

    private static void appendStringProperty(StringBuilder json, String name, String value) {
        appendPropertyPrefix(json, name);
        appendQuoted(json, value);
    }

    private static void appendNumberProperty(StringBuilder json, String name, long value) {
        appendPropertyPrefix(json, name);
        json.append(value);
    }

    private static void appendNullableNumberProperty(
            StringBuilder json,
            String name,
            Integer value
    ) {
        appendPropertyPrefix(json, name);
        if (value == null) {
            json.append("null");
        } else {
            json.append(value);
        }
    }

    private static void appendBooleanProperty(StringBuilder json, String name, boolean value) {
        appendPropertyPrefix(json, name);
        json.append(value);
    }

    private static void appendNullableBooleanProperty(
            StringBuilder json,
            String name,
            Boolean value
    ) {
        appendPropertyPrefix(json, name);
        if (value == null) {
            json.append("null");
        } else {
            json.append(value);
        }
    }

    private static void appendNullableDecimalProperty(
            StringBuilder json,
            String name,
            Double value
    ) {
        appendPropertyPrefix(json, name);
        if (value == null) {
            json.append("null");
        } else {
            json.append(Double.toString(value));
        }
    }

    private static void appendDecimalProperty(StringBuilder json, String name, float value) {
        appendPropertyPrefix(json, name);
        json.append(Float.toString(value));
    }

    private static void appendPropertyPrefix(StringBuilder json, String name) {
        if (json.charAt(json.length() - 1) != '{') {
            json.append(',');
        }
        appendQuoted(json, name);
        json.append(':');
    }

    private static void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20 || Character.isSurrogate(character)) {
                        appendUnicodeEscape(json, character);
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static void appendUnicodeEscape(StringBuilder json, char character) {
        json.append("\\u");
        for (int shift = 12; shift >= 0; shift -= 4) {
            json.append(Character.forDigit((character >>> shift) & 0x0f, 16));
        }
    }
}
