package dev.putrenkov.pdfaudit;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public final class PdfTextLayerAuditor {
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;
    public static final int DEFAULT_MAX_PAGE_COUNT = 1_000;
    public static final float DEFAULT_TINY_TEXT_THRESHOLD_POINTS = 3.0f;

    private final long maxFileSizeBytes;
    private final int maxPageCount;
    private final float tinyTextThresholdPoints;

    public PdfTextLayerAuditor() {
        this(
                DEFAULT_MAX_FILE_SIZE_BYTES,
                DEFAULT_MAX_PAGE_COUNT,
                DEFAULT_TINY_TEXT_THRESHOLD_POINTS);
    }

    public PdfTextLayerAuditor(long maxFileSizeBytes, int maxPageCount) {
        this(maxFileSizeBytes, maxPageCount, DEFAULT_TINY_TEXT_THRESHOLD_POINTS);
    }

    public PdfTextLayerAuditor(
            long maxFileSizeBytes,
            int maxPageCount,
            float tinyTextThresholdPoints
    ) {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be positive");
        }
        if (maxPageCount <= 0) {
            throw new IllegalArgumentException("maxPageCount must be positive");
        }
        if (!Float.isFinite(tinyTextThresholdPoints) || tinyTextThresholdPoints < 0) {
            throw new IllegalArgumentException(
                    "tinyTextThresholdPoints must be finite and non-negative");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxPageCount = maxPageCount;
        this.tinyTextThresholdPoints = tinyTextThresholdPoints;
    }

    public AuditReport audit(Path input) throws IOException {
        return audit(input, PageSelection.all());
    }

    public AuditReport audit(Path input, PageSelection pageSelection) throws IOException {
        if (pageSelection == null) {
            throw new IllegalArgumentException("pageSelection must not be null");
        }
        Path file = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("PDF file does not exist: " + file);
        }

        long fileSize = Files.size(file);
        if (fileSize > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                    "PDF exceeds the configured size limit of " + maxFileSizeBytes + " bytes");
        }

        try (PDDocument document = Loader.loadPDF(
                file.toFile(),
                IOUtils.createTempFileOnlyStreamCache())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new IllegalArgumentException("PDF contains no pages");
            }
            if (pageCount > maxPageCount) {
                throw new IllegalArgumentException(
                        "PDF exceeds the configured page limit of " + maxPageCount);
            }

            boolean extractionAllowed = document.getCurrentAccessPermission().canExtractContent();
            if (!extractionAllowed) {
                throw new SecurityException("PDF permissions do not allow text extraction");
            }

            List<Integer> selectedPages = pageSelection.resolve(pageCount);
            PositionCollector collector = new PositionCollector(
                    selectedPages,
                    tinyTextThresholdPoints);
            collector.writeText(document, Writer.nullWriter());

            return new AuditReport(
                    file,
                    fileSize,
                    pageCount,
                    document.isEncrypted(),
                    extractionAllowed,
                    tinyTextThresholdPoints,
                    collector.pages());
        }
    }

    private static final class PositionCollector extends PDFTextStripper {
        private final Map<Integer, MutablePage> pages = new LinkedHashMap<>();
        private final Set<Integer> selectedPageNumbers;
        private MutablePage currentPage;

        private PositionCollector(
                List<Integer> selectedPages,
                float tinyTextThresholdPoints
        ) {
            selectedPageNumbers = Set.copyOf(selectedPages);
            for (int pageNumber : selectedPages) {
                pages.put(
                        pageNumber,
                        new MutablePage(pageNumber, tinyTextThresholdPoints));
            }
            setSortByPosition(false);
            setSuppressDuplicateOverlappingText(false);
        }

        @Override
        public void processPage(PDPage page) throws IOException {
            if (selectedPageNumbers.contains(getCurrentPageNo())) {
                super.processPage(page);
            }
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            super.startPage(page);
            currentPage = pages.get(getCurrentPageNo());
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            currentPage.accept(text);
            super.processTextPosition(text);
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            currentPage = null;
            super.endPage(page);
        }

        private List<PageAudit> pages() {
            return pages.values().stream().map(MutablePage::freeze).toList();
        }
    }

    private static final class MutablePage {
        private final int pageNumber;
        private final float tinyTextThresholdPoints;
        private final Map<FontKey, MutableFont> fonts = new LinkedHashMap<>();
        private int glyphCount;
        private int unicodeCharacterCount;
        private int missingUnicodeGlyphCount;
        private int replacementCharacterCount;
        private int tinyTextGlyphCount;

        private MutablePage(int pageNumber, float tinyTextThresholdPoints) {
            this.pageNumber = pageNumber;
            this.tinyTextThresholdPoints = tinyTextThresholdPoints;
        }

        private void accept(TextPosition text) {
            glyphCount++;

            String unicode = text.getUnicode();
            if (unicode == null
                    || unicode.isEmpty()
                    || unicode.codePoints().anyMatch(Character::isISOControl)) {
                missingUnicodeGlyphCount++;
            }
            if (unicode != null && !unicode.isEmpty()) {
                unicodeCharacterCount += unicode.codePointCount(0, unicode.length());
                replacementCharacterCount += (int) unicode.codePoints()
                        .filter(codePoint -> codePoint == 0xFFFD)
                        .count();
            }

            if (tinyTextThresholdPoints > 0
                    && text.getFontSizeInPt() < tinyTextThresholdPoints) {
                tinyTextGlyphCount++;
            }

            PDFont font = text.getFont();
            String fontName = displayName(font);
            boolean embedded = font != null && font.isEmbedded();
            boolean damaged = font != null && font.isDamaged();
            FontKey key = new FontKey(fontName, embedded, damaged);
            MutableFont fontAudit = fonts.computeIfAbsent(
                    key,
                    ignored -> new MutableFont(
                            fontName,
                            embedded,
                            damaged));
            fontAudit.glyphCount++;
        }

        private static String displayName(PDFont font) {
            if (font == null) {
                return "<unknown>";
            }
            String name = font.getName();
            return name == null || name.isBlank() ? "<unnamed>" : name;
        }

        private PageAudit freeze() {
            List<Finding> findings = new ArrayList<>();
            if (glyphCount == 0) {
                findings.add(Finding.NO_TEXT_LAYER);
            }
            if (missingUnicodeGlyphCount > 0) {
                findings.add(Finding.MISSING_UNICODE);
            }
            if (replacementCharacterCount > 0) {
                findings.add(Finding.REPLACEMENT_CHARACTERS);
            }
            if (tinyTextGlyphCount > 0) {
                findings.add(Finding.TINY_TEXT);
            }

            List<FontAudit> fontAudits = fonts.values().stream()
                    .map(MutableFont::freeze)
                    .sorted(Comparator.comparing(FontAudit::name)
                            .thenComparing(FontAudit::embedded)
                            .thenComparing(FontAudit::damaged))
                    .toList();

            return new PageAudit(
                    pageNumber,
                    glyphCount,
                    unicodeCharacterCount,
                    missingUnicodeGlyphCount,
                    replacementCharacterCount,
                    tinyTextGlyphCount,
                    fontAudits,
                    findings);
        }
    }

    private record FontKey(String name, boolean embedded, boolean damaged) {
    }

    private static final class MutableFont {
        private final String name;
        private final boolean embedded;
        private final boolean damaged;
        private int glyphCount;

        private MutableFont(String name, boolean embedded, boolean damaged) {
            this.name = name;
            this.embedded = embedded;
            this.damaged = damaged;
        }

        private FontAudit freeze() {
            return new FontAudit(name, embedded, damaged, glyphCount);
        }
    }
}
