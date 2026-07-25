package dev.putrenkov.pdfaudit;

import java.io.PrintStream;
import java.util.Locale;

public final class TextReportPrinter {
    public void print(AuditReport report, PrintStream out) {
        out.println("PDF Text Layer Audit");
        out.println("File: " + report.file());
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
        out.println();

        for (PageAudit page : report.pages()) {
            out.printf(
                    Locale.ROOT,
                    "Page %d: %d glyphs, %d Unicode characters, %d fonts%n",
                    page.pageNumber(),
                    page.glyphCount(),
                    page.unicodeCharacterCount(),
                    page.fonts().size());

            for (FontAudit font : page.fonts()) {
                out.printf(
                        Locale.ROOT,
                        "  Font: %s | embedded=%s | damaged=%s | glyphs=%d%n",
                        font.name(),
                        font.embedded(),
                        font.damaged(),
                        font.glyphCount());
            }

            if (page.findings().isEmpty()) {
                out.println("  OK: no basic text-layer problems detected");
            } else {
                for (Finding finding : page.findings()) {
                    out.printf("  WARN %s: %s%n", finding.name(), finding.description());
                }
            }
        }

        out.println();
        if (report.needsAttention()) {
            out.printf(
                    Locale.ROOT,
                    "Result: %d of %d pages need attention%n",
                    report.pagesNeedingAttention(),
                    report.pages().size());
        } else {
            out.println("Result: no inspected text-layer problems were found");
        }
    }
}
