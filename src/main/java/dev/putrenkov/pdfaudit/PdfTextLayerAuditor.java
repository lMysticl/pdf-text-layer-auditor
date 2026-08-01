package dev.putrenkov.pdfaudit;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.cmap.CMapParser;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
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
            PositionOrderCollector positionOrder = new PositionOrderCollector(selectedPages);
            positionOrder.writeText(document, Writer.nullWriter());
            Map<Integer, PageVisualAnalyzer.PageEvidence> visualEvidence =
                    PageVisualAnalyzer.analyze(document, selectedPages);

            List<ParseDiagnostic> diagnostics = collector.diagnostics();

            return new AuditReport(
                    file,
                    fileSize,
                    pageCount,
                    document.isEncrypted(),
                    extractionAllowed,
                    tinyTextThresholdPoints,
                    new ParseHealth(true, !diagnostics.isEmpty(), diagnostics),
                    EvidenceCompleteness.phaseTwo(visualEvidence.values().stream()
                            .allMatch(evidence -> evidence.optionalContent().complete())),
                    collector.pages(positionOrder, visualEvidence));
        }
    }

    private static final class PositionCollector extends PDFTextStripper {
        private final Map<Integer, MutablePage> pages = new LinkedHashMap<>();
        private final Set<Integer> selectedPageNumbers;
        private final Deque<ActualTextFrame> actualTextFrames = new ArrayDeque<>();
        private MutablePage currentPage;
        private boolean malformedFontFallback;
        private boolean fontSelectionFailed;
        private int formDepth;

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
            malformedFontFallback = false;
            fontSelectionFailed = false;
            actualTextFrames.clear();
            formDepth = 0;
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands)
                throws IOException {
            if (OperatorName.SET_FONT_AND_SIZE.equals(operator.getName())) {
                fontSelectionFailed = false;
                super.processOperator(operator, operands);
                if (!fontSelectionFailed) {
                    malformedFontFallback = false;
                    currentPage.inspectFont(getGraphicsState().getTextState().getFont());
                }
                return;
            }
            super.processOperator(operator, operands);
        }

        @Override
        protected void operatorException(
                Operator operator,
                List<COSBase> operands,
                IOException exception
        ) throws IOException {
            if (OperatorName.SET_FONT_AND_SIZE.equals(operator.getName())) {
                fontSelectionFailed = true;
                if (isRecoverableMalformedType0Font(exception)) {
                    malformedFontFallback = true;
                    getGraphicsState().getTextState().setFont(null);
                    return;
                }
            }
            super.operatorException(operator, operands, exception);
        }

        private static boolean isRecoverableMalformedType0Font(IOException exception) {
            return switch (exception.getMessage()) {
                case "Missing descendant font array",
                        "Descendant font array is empty",
                        "Missing descendant font dictionary",
                        "Missing or wrong type in descendant font dictionary" -> true;
                default -> false;
            };
        }

        @Override
        public void beginMarkedContentSequence(COSName tag, COSDictionary properties) {
            String actualText = properties == null
                    ? null
                    : properties.getString(COSName.ACTUAL_TEXT);
            actualTextFrames.push(new ActualTextFrame(actualText));
            super.beginMarkedContentSequence(tag, properties);
        }

        @Override
        public void endMarkedContentSequence() {
            try {
                super.endMarkedContentSequence();
            } finally {
                if (!actualTextFrames.isEmpty()) {
                    actualTextFrames.pop();
                }
            }
        }

        @Override
        public void showForm(PDFormXObject form) throws IOException {
            formDepth++;
            try {
                super.showForm(form);
            } finally {
                formDepth--;
            }
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            String rawUnicode = text.getUnicode();
            ActualTextFrame actualText = activeActualText();
            boolean actualTextActive = actualText != null;
            String semanticOverride = actualTextActive ? actualText.consume() : null;
            super.processTextPosition(text);
            currentPage.accept(
                    text,
                    rawUnicode,
                    malformedFontFallback,
                    formDepth > 0,
                    actualTextActive,
                    semanticOverride);
        }

        private ActualTextFrame activeActualText() {
            for (ActualTextFrame frame : actualTextFrames) {
                if (frame.text() != null) {
                    return frame;
                }
            }
            return null;
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            currentPage = null;
            malformedFontFallback = false;
            fontSelectionFailed = false;
            actualTextFrames.clear();
            formDepth = 0;
            super.endPage(page);
        }

        private List<PageAudit> pages(
                PositionOrderCollector positionOrder,
                Map<Integer, PageVisualAnalyzer.PageEvidence> visualEvidence
        ) {
            return pages.values().stream()
                    .map(page -> page.freeze(
                            positionOrder.text(page.pageNumber),
                            visualEvidence.get(page.pageNumber)))
                    .toList();
        }

        private List<ParseDiagnostic> diagnostics() {
            return pages.values().stream()
                    .flatMap(page -> page.diagnostics.stream())
                    .sorted(Comparator.comparingInt(ParseDiagnostic::pageNumber)
                            .thenComparing(ParseDiagnostic::fontName)
                            .thenComparing(diagnostic -> diagnostic.code().name()))
                    .toList();
        }
    }

    private static final class PositionOrderCollector extends PDFTextStripper {
        private final Map<Integer, StringBuilder> pages = new LinkedHashMap<>();
        private final Set<Integer> selectedPageNumbers;
        private StringBuilder currentPage;

        private PositionOrderCollector(List<Integer> selectedPages) {
            selectedPageNumbers = Set.copyOf(selectedPages);
            for (int pageNumber : selectedPages) {
                pages.put(pageNumber, new StringBuilder());
            }
            setSortByPosition(true);
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
        protected void writeString(String text) {
            currentPage.append(text);
        }

        @Override
        protected void operatorException(
                Operator operator,
                List<COSBase> operands,
                IOException exception
        ) throws IOException {
            if (OperatorName.SET_FONT_AND_SIZE.equals(operator.getName())
                    && PositionCollector.isRecoverableMalformedType0Font(exception)) {
                getGraphicsState().getTextState().setFont(null);
                return;
            }
            super.operatorException(operator, operands, exception);
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            currentPage = null;
            super.endPage(page);
        }

        private String text(int pageNumber) {
            return pages.get(pageNumber).toString();
        }
    }

    private static final class ActualTextFrame {
        private final String text;
        private boolean consumed;

        private ActualTextFrame(String text) {
            this.text = text == null ? null : text.replace("\u00ad", "");
        }

        private String text() {
            return text;
        }

        private String consume() {
            if (consumed) {
                return "";
            }
            consumed = true;
            return text;
        }
    }

    private static final class MutablePage {
        private final int pageNumber;
        private final float tinyTextThresholdPoints;
        private final Map<FontKey, MutableFont> fonts = new LinkedHashMap<>();
        private final Set<COSDictionary> inspectedFonts =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<ParseDiagnostic> diagnostics = new ArrayList<>();
        private final StringBuilder streamSemanticText = new StringBuilder();
        private int glyphCount;
        private int unicodeCharacterCount;
        private int missingUnicodeGlyphCount;
        private int replacementCharacterCount;
        private int tinyTextGlyphCount;
        private int formXObjectGlyphCount;
        private int actualTextGlyphCount;
        private int actualTextCharacterCount;
        private int rawMappedGlyphCount;
        private int rawUnmappedGlyphCount;
        private int actualTextResolvedGlyphCount;
        private int malformedToUnicodeFontCount;

        private MutablePage(int pageNumber, float tinyTextThresholdPoints) {
            this.pageNumber = pageNumber;
            this.tinyTextThresholdPoints = tinyTextThresholdPoints;
        }

        private void accept(
                TextPosition text,
                String rawUnicode,
                boolean mappingUntrusted,
                boolean inFormXObject,
                boolean actualTextActive,
                String semanticOverride
        ) {
            glyphCount++;
            if (inFormXObject) {
                formXObjectGlyphCount++;
            }

            boolean rawMappingMissing = mappingUntrusted
                    || hasMissingFontMapping(text, rawUnicode);
            if (rawMappingMissing) {
                rawUnmappedGlyphCount++;
            } else {
                rawMappedGlyphCount++;
            }
            if (actualTextActive) {
                actualTextGlyphCount++;
                if (rawMappingMissing) {
                    actualTextResolvedGlyphCount++;
                }
            } else if (rawMappingMissing) {
                missingUnicodeGlyphCount++;
            }

            String unicode = actualTextActive ? semanticOverride : rawUnicode;
            if (unicode != null && !unicode.isEmpty()) {
                int semanticCharacters = unicode.codePointCount(0, unicode.length());
                unicodeCharacterCount += semanticCharacters;
                if (actualTextActive) {
                    actualTextCharacterCount += semanticCharacters;
                }
                streamSemanticText.append(unicode);
                replacementCharacterCount += (int) unicode.codePoints()
                        .filter(codePoint -> codePoint == 0xFFFD)
                        .count();
            }

            if (tinyTextThresholdPoints > 0
                    && text.getFontSizeInPt() < tinyTextThresholdPoints) {
                tinyTextGlyphCount++;
            }

            PDFont font = text.getFont();
            String fontName = mappingUntrusted ? "<malformed-font>" : displayName(font);
            boolean embedded = !mappingUntrusted && font != null && font.isEmbedded();
            boolean damaged = mappingUntrusted || font != null && font.isDamaged();
            FontKey key = new FontKey(fontName, embedded, damaged);
            MutableFont fontAudit = fonts.computeIfAbsent(
                    key,
                    ignored -> new MutableFont(
                            fontName,
                            embedded,
                            damaged));
            fontAudit.glyphCount++;
        }

        private void inspectFont(PDFont font) {
            if (font == null || !inspectedFonts.add(font.getCOSObject())) {
                return;
            }
            ParseDiagnosticCode diagnosticCode = inspectToUnicode(font.getCOSObject());
            if (diagnosticCode != null) {
                malformedToUnicodeFontCount++;
                diagnostics.add(new ParseDiagnostic(
                        diagnosticCode,
                        pageNumber,
                        displayName(font)));
            }
        }

        private static ParseDiagnosticCode inspectToUnicode(COSDictionary fontDictionary) {
            COSBase toUnicode = fontDictionary.getDictionaryObject(COSName.TO_UNICODE);
            if (toUnicode == null) {
                return null;
            }
            try {
                CMap cmap;
                if (toUnicode instanceof COSName name) {
                    cmap = new CMapParser().parsePredefined(name.getName());
                } else if (toUnicode instanceof COSStream stream) {
                    try (RandomAccessRead input = stream.createView()) {
                        cmap = new CMapParser().parse(input);
                    }
                } else {
                    return ParseDiagnosticCode.MALFORMED_TOUNICODE_CMAP;
                }
                return cmap.hasUnicodeMappings()
                        ? null
                        : ParseDiagnosticCode.INVALID_TOUNICODE_CMAP;
            } catch (IOException | RuntimeException exception) {
                return ParseDiagnosticCode.MALFORMED_TOUNICODE_CMAP;
            }
        }

        private static boolean hasMissingFontMapping(
                TextPosition text,
                String extractedUnicode
        ) {
            PDFont font = text.getFont();
            int[] codes = text.getCharacterCodes();
            if (font == null || codes == null || codes.length == 0) {
                return !isUsableUnicode(extractedUnicode);
            }
            try {
                for (int code : codes) {
                    if (!isUsableUnicode(font.toUnicode(code))) {
                        return true;
                    }
                }
                return false;
            } catch (RuntimeException exception) {
                return true;
            }
        }

        private static String displayName(PDFont font) {
            if (font == null) {
                return "<unknown>";
            }
            String name = font.getName();
            return name == null || name.isBlank() ? "<unnamed>" : name;
        }

        private PageAudit freeze(
                String positionText,
                PageVisualAnalyzer.PageEvidence visualEvidence
        ) {
            VisualContentAudit visualContent = visualEvidence.visualContent();
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
            if (diagnostics.stream().anyMatch(
                    diagnostic -> diagnostic.code()
                            == ParseDiagnosticCode.MALFORMED_TOUNICODE_CMAP)) {
                findings.add(Finding.MALFORMED_TOUNICODE_CMAP);
            }
            if (diagnostics.stream().anyMatch(
                    diagnostic -> diagnostic.code()
                            == ParseDiagnosticCode.INVALID_TOUNICODE_CMAP)) {
                findings.add(Finding.INVALID_TOUNICODE_CMAP);
            }

            String canonicalStreamText = canonicalReadingOrderText(streamSemanticText.toString());
            String canonicalPositionText = canonicalReadingOrderText(positionText);
            boolean readingOrderDiverges = !canonicalStreamText.equals(canonicalPositionText);
            if (readingOrderDiverges) {
                findings.add(Finding.READING_ORDER_DIVERGENCE);
            }
            PageClassification classification = classify(visualContent);
            if (classification == PageClassification.SPARSE_OCR) {
                findings.add(Finding.SPARSE_TEXT_OVER_FULL_PAGE_IMAGE);
            }
            AnnotationAppearanceAudit annotationAppearances =
                    visualEvidence.annotationAppearances();
            if (annotationAppearances.missingUnicodeGlyphCount() > 0) {
                findings.add(Finding.ANNOTATION_MISSING_UNICODE);
            }
            if (annotationAppearances.replacementCharacterCount() > 0) {
                findings.add(Finding.ANNOTATION_REPLACEMENT_CHARACTERS);
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
                    classification,
                    new TextSurfaceAudit(
                            glyphCount - formXObjectGlyphCount,
                            formXObjectGlyphCount,
                            actualTextGlyphCount,
                            actualTextCharacterCount),
                    new SemanticMappingAudit(
                            rawMappedGlyphCount,
                            rawUnmappedGlyphCount,
                            actualTextResolvedGlyphCount,
                            malformedToUnicodeFontCount),
                    new ReadingOrderAudit(
                            true,
                            readingOrderDiverges,
                            canonicalStreamText.codePointCount(0, canonicalStreamText.length()),
                            canonicalPositionText.codePointCount(0, canonicalPositionText.length())),
                    visualEvidence.geometryVisibility(),
                    visualContent,
                    annotationAppearances,
                    visualEvidence.optionalContent(),
                    fontAudits,
                    findings);
        }

        private PageClassification classify(VisualContentAudit visualContent) {
            int images = visualContent.imageCount();
            int vectors = visualContent.paintedVectorPathCount();
            if (glyphCount == 0) {
                if (images > 0) {
                    return PageClassification.IMAGE_ONLY;
                }
                if (vectors > 0) {
                    return PageClassification.VECTOR_ONLY;
                }
                return PageClassification.BLANK;
            }
            if (images == 0) {
                return PageClassification.NATIVE_TEXT;
            }
            if (visualContent.combinedImageCoverageRatio() >= 0.75
                    && unicodeCharacterCount <= 32) {
                return PageClassification.SPARSE_OCR;
            }
            return PageClassification.MIXED;
        }

    }

    private static String canonicalReadingOrderText(String text) {
        StringBuilder canonical = new StringBuilder(text.length());
        text.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint)
                        && !Character.isSpaceChar(codePoint))
                .forEach(canonical::appendCodePoint);
        return canonical.toString();
    }

    static boolean isUsableUnicode(String unicode) {
        if (unicode == null || unicode.isEmpty()) {
            return false;
        }
        return unicode.codePoints().allMatch(codePoint ->
                codePoint != 0xFFFD
                        && !Character.isISOControl(codePoint)
                        && Character.isDefined(codePoint)
                        && Character.getType(codePoint) != Character.PRIVATE_USE
                        && Character.getType(codePoint) != Character.SURROGATE
                        && !isUnicodeNoncharacter(codePoint));
    }

    private static boolean isUnicodeNoncharacter(int codePoint) {
        return codePoint >= 0xFDD0 && codePoint <= 0xFDEF
                || codePoint >= 0 && (codePoint & 0xFFFE) == 0xFFFE;
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
