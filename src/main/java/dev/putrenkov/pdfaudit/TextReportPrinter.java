package dev.putrenkov.pdfaudit;

import java.io.PrintStream;
import java.util.Locale;

public final class TextReportPrinter {
    public void print(AuditReport report, PrintStream out) {
        out.println("PDF Text Layer Audit");
        out.println("File: " + TerminalText.escape(String.valueOf(report.file())));
        out.printf(Locale.ROOT, "Size: %.2f MiB%n", report.fileSizeBytes() / 1024.0 / 1024.0);
        out.println("Pages in document: " + report.pageCount());
        out.println("Pages inspected: " + report.pages().size());
        out.println("Encrypted: " + report.encrypted());
        if (report.tinyTextThresholdPoints() == 0) {
            out.println("Tiny text threshold: disabled");
        } else {
            out.printf(
                    Locale.ROOT,
                    "Tiny text threshold: %.2f pt%n",
                    report.tinyTextThresholdPoints());
        }
        out.println("Parse complete: " + report.parseHealth().complete());
        out.println("Parse recovered: " + report.parseHealth().recovered());
        out.println("Typed parse diagnostics: " + report.parseHealth().diagnostics().size());
        out.println("Evidence complete for direct routing: "
                + report.completeness().completeForDirectRouting());
        out.println();

        for (PageAudit page : report.pages()) {
            int fontCount = page.fonts().size();
            out.printf(
                    Locale.ROOT,
                    "Page %d: %d glyphs, %d Unicode characters, %d %s%n",
                    page.pageNumber(),
                    page.glyphCount(),
                    page.unicodeCharacterCount(),
                    fontCount,
                    fontCount == 1 ? "font" : "fonts");
            out.println("  Classification: " + page.classification());
            out.printf(
                    Locale.ROOT,
                    "  Text surfaces: page=%d, forms=%d, ActualText glyphs=%d%n",
                    page.textSurfaces().pageContentGlyphCount(),
                    page.textSurfaces().formXObjectGlyphCount(),
                    page.textSurfaces().actualTextGlyphCount());
            out.printf(
                    Locale.ROOT,
                    "  Raw Unicode mapping: mapped=%d, unmapped=%d, ActualText-resolved=%d%n",
                    page.semanticMapping().rawMappedGlyphCount(),
                    page.semanticMapping().rawUnmappedGlyphCount(),
                    page.semanticMapping().actualTextResolvedGlyphCount());
            out.printf(
                    Locale.ROOT,
                    "  Reading order: assessed=%s, diverges=%s%n",
                    page.readingOrder().assessed(),
                    page.readingOrder().diverges());

            for (FontAudit font : page.fonts()) {
                out.printf(
                        Locale.ROOT,
                        "  Font: %s | embedded=%s | damaged=%s | glyphs=%d%n",
                        TerminalText.escape(font.name()),
                        font.embedded(),
                        font.damaged(),
                        font.glyphCount());
            }

            if (page.findings().isEmpty()) {
                out.println("  OK: no problems found in the assessed evidence");
            } else {
                for (Finding finding : page.findings()) {
                    out.printf("  WARN %s: %s%n", finding.name(), finding.description());
                }
            }
        }

        out.println();
        if (report.needsAttention()) {
            int inspectedPageCount = report.pages().size();
            out.printf(
                    Locale.ROOT,
                    "Result: %d of %d %s %s attention%n",
                    report.pagesNeedingAttention(),
                    inspectedPageCount,
                    inspectedPageCount == 1 ? "page" : "pages",
                    inspectedPageCount == 1 ? "needs" : "need");
        } else {
            out.println("Result: no inspected text-layer problems were found");
        }
    }
}
