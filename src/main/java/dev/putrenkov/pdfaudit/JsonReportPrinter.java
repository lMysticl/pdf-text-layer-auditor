package dev.putrenkov.pdfaudit;

import java.io.PrintStream;
import java.util.Objects;

public final class JsonReportPrinter {
    public static final int SCHEMA_VERSION = 5;

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
        appendDocumentSurfaces(json, report.documentSurfaces());
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
        appendNumberProperty(json, "parserWarningCount", health.parserWarningCount());
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

    private static void appendDocumentSurfaces(
            StringBuilder json,
            DocumentSurfaceAudit surfaces
    ) {
        appendPropertyPrefix(json, "documentSurfaces");
        json.append('{');
        appendBooleanProperty(json, "assessed", surfaces.assessed());
        appendBooleanProperty(json, "complete", surfaces.complete());
        appendNullableNumberProperty(json, "acroFormFieldCount", surfaces.acroFormFieldCount());
        appendNullableNumberProperty(json, "signatureFieldCount", surfaces.signatureFieldCount());
        appendNullableNumberProperty(
                json,
                "widgetWithoutAppearanceCount",
                surfaces.widgetWithoutAppearanceCount());
        appendNullableBooleanProperty(json, "xfaPresent", surfaces.xfaPresent());
        appendNullableNumberProperty(json, "embeddedFileCount", surfaces.embeddedFileCount());
        appendNullableNumberProperty(
                json,
                "associatedFileReferenceCount",
                surfaces.associatedFileReferenceCount());
        appendNullableBooleanProperty(json, "portfolioPresent", surfaces.portfolioPresent());
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
        appendAnnotationAppearances(json, page.annotationAppearances());
        appendOptionalContent(json, page.optionalContent());
        appendUnicodeProfile(json, page.unicodeProfile());
        appendSpatialEvidence(json, page.spatialEvidence());
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
        appendNumberProperty(
                json,
                "implicitCompositeMappingGlyphCount",
                mapping.implicitCompositeMappingGlyphCount());
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
                "imageOccupiedGridCellCount",
                visual.imageOccupiedGridCellCount());
        appendNullableNumberProperty(
                json,
                "imageTextOverlapGridCellCount",
                visual.imageTextOverlapGridCellCount());
        appendNullableDecimalProperty(
                json,
                "imageTextOverlapRatio",
                visual.imageTextOverlapRatio());
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

    private static void appendAnnotationAppearances(
            StringBuilder json,
            AnnotationAppearanceAudit annotations
    ) {
        appendPropertyPrefix(json, "annotationAppearances");
        json.append('{');
        appendBooleanProperty(json, "assessed", annotations.assessed());
        appendNullableNumberProperty(
                json, "appearanceStreamCount", annotations.appearanceStreamCount());
        appendNullableNumberProperty(json, "glyphCount", annotations.glyphCount());
        appendNullableNumberProperty(
                json, "unicodeCharacterCount", annotations.unicodeCharacterCount());
        appendNullableNumberProperty(
                json, "missingUnicodeGlyphCount", annotations.missingUnicodeGlyphCount());
        appendNullableNumberProperty(
                json, "replacementCharacterCount", annotations.replacementCharacterCount());
        json.append('}');
    }

    private static void appendOptionalContent(
            StringBuilder json,
            OptionalContentAudit optionalContent
    ) {
        appendPropertyPrefix(json, "optionalContent");
        json.append('{');
        appendBooleanProperty(json, "complete", optionalContent.complete());
        appendNumberProperty(json, "referenceCount", optionalContent.referenceCount());
        appendNumberProperty(
                json,
                "membershipReferenceCount",
                optionalContent.membershipReferenceCount());
        appendNumberProperty(
                json,
                "hiddenInViewReferenceCount",
                optionalContent.hiddenInViewReferenceCount());
        appendNumberProperty(
                json,
                "hiddenInPrintReferenceCount",
                optionalContent.hiddenInPrintReferenceCount());
        appendNumberProperty(
                json,
                "hiddenInExportReferenceCount",
                optionalContent.hiddenInExportReferenceCount());
        appendNumberProperty(
                json,
                "evaluationFailureCount",
                optionalContent.evaluationFailureCount());
        json.append('}');
    }

    private static void appendUnicodeProfile(
            StringBuilder json,
            UnicodeProfileAudit profile
    ) {
        appendPropertyPrefix(json, "unicodeProfile");
        json.append("{\"scripts\":[");
        for (int index = 0; index < profile.scripts().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendQuoted(json, profile.scripts().get(index));
        }
        json.append(']');
        appendNumberProperty(
                json,
                "rightToLeftCharacterCount",
                profile.rightToLeftCharacterCount());
        appendNumberProperty(json, "combiningMarkCount", profile.combiningMarkCount());
        appendNumberProperty(json, "nonBmpCharacterCount", profile.nonBmpCharacterCount());
        appendNumberProperty(json, "variationSelectorCount", profile.variationSelectorCount());
        appendNumberProperty(json, "zeroWidthJoinerCount", profile.zeroWidthJoinerCount());
        appendNumberProperty(json, "bidiControlCount", profile.bidiControlCount());
        json.append('}');
    }

    private static void appendSpatialEvidence(
            StringBuilder json,
            SpatialEvidenceAudit spatial
    ) {
        appendPropertyPrefix(json, "spatialEvidence");
        json.append('{');
        appendBooleanProperty(json, "assessed", spatial.assessed());
        appendNullableDecimalProperty(json, "pageWidthPoints", spatial.pageWidthPoints());
        appendNullableDecimalProperty(json, "pageHeightPoints", spatial.pageHeightPoints());
        appendNullableNumberProperty(json, "rotationDegrees", spatial.rotationDegrees());
        appendNullableStringProperty(json, "coordinateSpace", spatial.coordinateSpace());
        appendNumberProperty(json, "totalLocationCount", spatial.totalLocationCount());
        appendBooleanProperty(json, "locationsTruncated", spatial.locationsTruncated());
        json.append(",\"locations\":[");
        for (int index = 0; index < spatial.locations().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            FindingLocation location = spatial.locations().get(index);
            json.append('{');
            appendStringProperty(json, "code", location.code().name());
            appendDecimalProperty(json, "xPoints", location.xPoints());
            appendDecimalProperty(json, "yPoints", location.yPoints());
            appendDecimalProperty(json, "widthPoints", location.widthPoints());
            appendDecimalProperty(json, "heightPoints", location.heightPoints());
            json.append('}');
        }
        json.append(']');
        appendVisualRegions(json, spatial.visualRegions());
        json.append('}');
    }

    private static void appendVisualRegions(
            StringBuilder json,
            VisualRegionAudit visualRegions
    ) {
        appendPropertyPrefix(json, "visualRegions");
        json.append('{');
        appendNumberProperty(
                json,
                "totalRegionCount",
                visualRegions.totalRegionCount());
        appendBooleanProperty(
                json,
                "regionsTruncated",
                visualRegions.regionsTruncated());
        appendPropertyPrefix(json, "counts");
        json.append('{');
        appendNumberProperty(json, "imageCount", visualRegions.counts().imageCount());
        appendNumberProperty(
                json,
                "annotationCount",
                visualRegions.counts().annotationCount());
        appendNumberProperty(
                json,
                "formFieldCount",
                visualRegions.counts().formFieldCount());
        json.append('}');
        json.append(",\"regions\":[");
        for (int index = 0; index < visualRegions.regions().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            VisualRegion region = visualRegions.regions().get(index);
            json.append('{');
            appendStringProperty(json, "type", region.type().name());
            appendDecimalProperty(json, "xPoints", region.xPoints());
            appendDecimalProperty(json, "yPoints", region.yPoints());
            appendDecimalProperty(json, "widthPoints", region.widthPoints());
            appendDecimalProperty(json, "heightPoints", region.heightPoints());
            json.append('}');
        }
        json.append("]}");
    }

    private static void appendFont(StringBuilder json, FontAudit font) {
        json.append('{');
        appendStringProperty(json, "name", font.name());
        appendStringProperty(json, "subtype", font.subtype());
        appendStringProperty(json, "encoding", font.encoding());
        appendBooleanProperty(json, "embedded", font.embedded());
        appendBooleanProperty(json, "damaged", font.damaged());
        appendBooleanProperty(json, "vertical", font.vertical());
        appendBooleanProperty(json, "toUnicodePresent", font.toUnicodePresent());
        appendBooleanProperty(json, "subset", font.subset());
        appendNumberProperty(json, "glyphCount", font.glyphCount());
        appendNumberProperty(json, "rawUnmappedGlyphCount", font.rawUnmappedGlyphCount());
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

    private static void appendNullableStringProperty(
            StringBuilder json,
            String name,
            String value
    ) {
        appendPropertyPrefix(json, name);
        if (value == null) {
            json.append("null");
        } else {
            appendQuoted(json, value);
        }
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

    private static void appendDecimalProperty(StringBuilder json, String name, double value) {
        appendPropertyPrefix(json, name);
        json.append(Double.toString(value));
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
