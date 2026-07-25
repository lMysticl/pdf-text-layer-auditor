package dev.putrenkov.pdfaudit;

import java.io.PrintStream;
import java.util.Objects;

public final class JsonReportPrinter {
    public void print(AuditReport report, PrintStream out) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(out, "out");
        out.println(toJson(report));
    }

    static String toJson(AuditReport report) {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        appendStringProperty(json, "file", report.file().toString());
        appendNumberProperty(json, "fileSizeBytes", report.fileSizeBytes());
        appendNumberProperty(json, "pageCount", report.pageCount());
        appendBooleanProperty(json, "encrypted", report.encrypted());
        appendBooleanProperty(json, "extractionAllowed", report.extractionAllowed());
        appendDecimalProperty(
                json,
                "tinyTextThresholdPoints",
                report.tinyTextThresholdPoints());
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

    private static void appendPage(StringBuilder json, PageAudit page) {
        json.append('{');
        appendNumberProperty(json, "pageNumber", page.pageNumber());
        appendNumberProperty(json, "glyphCount", page.glyphCount());
        appendNumberProperty(json, "unicodeCharacterCount", page.unicodeCharacterCount());
        appendNumberProperty(json, "missingUnicodeGlyphCount", page.missingUnicodeGlyphCount());
        appendNumberProperty(json, "replacementCharacterCount", page.replacementCharacterCount());
        appendNumberProperty(json, "tinyTextGlyphCount", page.tinyTextGlyphCount());
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

    private static void appendBooleanProperty(StringBuilder json, String name, boolean value) {
        appendPropertyPrefix(json, name);
        json.append(value);
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
