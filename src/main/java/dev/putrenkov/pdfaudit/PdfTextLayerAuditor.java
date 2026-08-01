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
    private final AuditWorkLimits workLimits;

    public PdfTextLayerAuditor() {
        this(
                DEFAULT_MAX_FILE_SIZE_BYTES,
                DEFAULT_MAX_PAGE_COUNT,
                DEFAULT_TINY_TEXT_THRESHOLD_POINTS,
                AuditWorkLimits.defaults());
    }

    public PdfTextLayerAuditor(long maxFileSizeBytes, int maxPageCount) {
        this(
                maxFileSizeBytes,
                maxPageCount,
                DEFAULT_TINY_TEXT_THRESHOLD_POINTS,
                AuditWorkLimits.defaults());
    }

    public PdfTextLayerAuditor(
            long maxFileSizeBytes,
            int maxPageCount,
            float tinyTextThresholdPoints
    ) {
        this(
                maxFileSizeBytes,
                maxPageCount,
                tinyTextThresholdPoints,
                AuditWorkLimits.defaults());
    }

    public PdfTextLayerAuditor(
            long maxFileSizeBytes,
            int maxPageCount,
            float tinyTextThresholdPoints,
            AuditWorkLimits workLimits
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
        this.workLimits = java.util.Objects.requireNonNull(workLimits, "workLimits");
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
                    tinyTextThresholdPoints,
                    workLimits);
            collector.writeText(document, Writer.nullWriter());
            PositionOrderCollector positionOrder = new PositionOrderCollector(
                    selectedPages,
                    workLimits);
            positionOrder.writeText(document, Writer.nullWriter());
            Map<Integer, PageVisualAnalyzer.PageEvidence> visualEvidence =
                    PageVisualAnalyzer.analyze(document, selectedPages, workLimits);

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
        private final AuditWorkLimits workLimits;
        private long totalGlyphCount;
        private long totalSemanticCharacterCount;

        private PositionCollector(
                List<Integer> selectedPages,
                float tinyTextThresholdPoints,
                AuditWorkLimits workLimits
        ) {
            this.workLimits = workLimits;
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
            totalGlyphCount++;
            if (totalGlyphCount > workLimits.maximumGlyphCount()) {
                throw new AuditWorkLimitException(
                        AuditWorkLimitException.Code.GLYPH_COUNT,
                        workLimits.maximumGlyphCount());
            }
            String rawUnicode = text.getUnicode();
            ActualTextFrame actualText = activeActualText();
            boolean actualTextActive = actualText != null;
            String semanticOverride = actualTextActive ? actualText.consume() : null;
            String semanticText = actualTextActive ? semanticOverride : rawUnicode;
            if (semanticText != null) {
                totalSemanticCharacterCount +=
                        semanticText.codePointCount(0, semanticText.length());
                if (totalSemanticCharacterCount
                        > workLimits.maximumSemanticCharacterCount()) {
                    throw new AuditWorkLimitException(
                            AuditWorkLimitException.Code.SEMANTIC_CHARACTER_COUNT,
                            workLimits.maximumSemanticCharacterCount());
                }
            }
            super.processTextPosition(text);
            currentPage.accept(
                    text,
                    rawUnicode,
                    malformedFontFallback,
                    formDepth > 0,
                    actualTextActive,
                    semanticOverride,
                    workLimits);
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
        private final AuditWorkLimits workLimits;
        private long semanticCharacterCount;
        private StringBuilder currentPage;

        private PositionOrderCollector(
                List<Integer> selectedPages,
                AuditWorkLimits workLimits
        ) {
            this.workLimits = workLimits;
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
            semanticCharacterCount += text.codePointCount(0, text.length());
            if (semanticCharacterCount > workLimits.maximumSemanticCharacterCount()) {
                throw new AuditWorkLimitException(
                        AuditWorkLimitException.Code.SEMANTIC_CHARACTER_COUNT,
                        workLimits.maximumSemanticCharacterCount());
            }
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
        private final Set<String> unicodeScripts = new java.util.TreeSet<>();
        private int rightToLeftCharacterCount;
        private int combiningMarkCount;
        private int nonBmpCharacterCount;
        private int variationSelectorCount;
        private int zeroWidthJoinerCount;
        private int bidiControlCount;

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
                String semanticOverride,
                AuditWorkLimits workLimits
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
                unicode.codePoints().forEach(this::acceptUnicodeCodePoint);
            }

            if (tinyTextThresholdPoints > 0
                    && text.getFontSizeInPt() < tinyTextThresholdPoints) {
                tinyTextGlyphCount++;
            }

            PDFont font = text.getFont();
            String fontName = mappingUntrusted ? "<malformed-font>" : displayName(font);
            boolean embedded = !mappingUntrusted && font != null && font.isEmbedded();
            boolean damaged = mappingUntrusted || font != null && font.isDamaged();
            FontDescriptor descriptor = mappingUntrusted
                    ? FontDescriptor.malformed()
                    : describeFont(font);
            FontKey key = new FontKey(
                    fontName,
                    descriptor.subtype(),
                    descriptor.encoding(),
                    embedded,
                    damaged,
                    descriptor.vertical(),
                    descriptor.toUnicodePresent(),
                    descriptor.subset());
            MutableFont fontAudit = fonts.get(key);
            if (fontAudit == null) {
                if (fonts.size() >= workLimits.maximumFontCount()) {
                    throw new AuditWorkLimitException(
                            AuditWorkLimitException.Code.FONT_COUNT,
                            workLimits.maximumFontCount());
                }
                fontAudit = new MutableFont(
                            fontName,
                            descriptor.subtype(),
                            descriptor.encoding(),
                            embedded,
                            damaged,
                            descriptor.vertical(),
                            descriptor.toUnicodePresent(),
                            descriptor.subset());
                fonts.put(key, fontAudit);
            }
            fontAudit.glyphCount++;
            if (rawMappingMissing) {
                fontAudit.rawUnmappedGlyphCount++;
            }
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
            return boundedName(name, "<unnamed>");
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
                            .thenComparing(FontAudit::subtype)
                            .thenComparing(FontAudit::encoding)
                            .thenComparing(FontAudit::embedded)
                            .thenComparing(FontAudit::damaged)
                            .thenComparing(FontAudit::vertical)
                            .thenComparing(FontAudit::toUnicodePresent)
                            .thenComparing(FontAudit::subset))
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
                    new UnicodeProfileAudit(
                            List.copyOf(unicodeScripts),
                            rightToLeftCharacterCount,
                            combiningMarkCount,
                            nonBmpCharacterCount,
                            variationSelectorCount,
                            zeroWidthJoinerCount,
                            bidiControlCount),
                    fontAudits,
                    findings);
        }

        private static FontDescriptor describeFont(PDFont font) {
            if (font == null) {
                return new FontDescriptor(
                        "<unknown>", "<implicit>", false, false, false);
            }
            COSDictionary dictionary = font.getCOSObject();
            String subtype = boundedName(dictionary.getNameAsString(COSName.SUBTYPE), "<unknown>");
            String encoding = describeEncoding(dictionary.getDictionaryObject(COSName.ENCODING));
            String name = font.getName();
            boolean subset = name != null && name.matches("^[A-Z]{6}\\+.+");
            return new FontDescriptor(
                    subtype,
                    encoding,
                    font.isVertical(),
                    dictionary.containsKey(COSName.TO_UNICODE),
                    subset);
        }

        private static String describeEncoding(COSBase encoding) {
            if (encoding == null) {
                return "<implicit>";
            }
            if (encoding instanceof COSName name) {
                return boundedName(name.getName(), "<unnamed>");
            }
            if (encoding instanceof COSStream stream) {
                return boundedName(
                        stream.getNameAsString(COSName.getPDFName("CMapName")),
                        "<stream>");
            }
            if (encoding instanceof COSDictionary dictionary) {
                return boundedName(
                        dictionary.getNameAsString(COSName.BASE_ENCODING),
                        "<dictionary>");
            }
            return "<other>";
        }

        private static String boundedName(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            StringBuilder safe = new StringBuilder(Math.min(value.length(), 128));
            value.codePoints()
                    .filter(codePoint -> !Character.isISOControl(codePoint))
                    .limit(128)
                    .forEach(safe::appendCodePoint);
            return safe.isEmpty() ? fallback : safe.toString();
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

        private void acceptUnicodeCodePoint(int codePoint) {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script != Character.UnicodeScript.COMMON
                    && script != Character.UnicodeScript.INHERITED
                    && script != Character.UnicodeScript.UNKNOWN) {
                unicodeScripts.add(script.name());
            }
            byte directionality = Character.getDirectionality(codePoint);
            if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                rightToLeftCharacterCount++;
            }
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                combiningMarkCount++;
            }
            if (codePoint > Character.MAX_VALUE) {
                nonBmpCharacterCount++;
            }
            if (codePoint >= 0xFE00 && codePoint <= 0xFE0F
                    || codePoint >= 0xE0100 && codePoint <= 0xE01EF) {
                variationSelectorCount++;
            }
            if (codePoint == 0x200D) {
                zeroWidthJoinerCount++;
            }
            if (isBidiControl(codePoint)) {
                bidiControlCount++;
            }
        }

        private static boolean isBidiControl(int codePoint) {
            return codePoint == 0x061C
                    || codePoint == 0x200E
                    || codePoint == 0x200F
                    || codePoint >= 0x202A && codePoint <= 0x202E
                    || codePoint >= 0x2066 && codePoint <= 0x2069;
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

    private record FontDescriptor(
            String subtype,
            String encoding,
            boolean vertical,
            boolean toUnicodePresent,
            boolean subset
    ) {
        private static FontDescriptor malformed() {
            return new FontDescriptor(
                    "<malformed>", "<unknown>", false, false, false);
        }
    }

    private record FontKey(
            String name,
            String subtype,
            String encoding,
            boolean embedded,
            boolean damaged,
            boolean vertical,
            boolean toUnicodePresent,
            boolean subset
    ) {
    }

    private static final class MutableFont {
        private final String name;
        private final String subtype;
        private final String encoding;
        private final boolean embedded;
        private final boolean damaged;
        private final boolean vertical;
        private final boolean toUnicodePresent;
        private final boolean subset;
        private int glyphCount;
        private int rawUnmappedGlyphCount;

        private MutableFont(
                String name,
                String subtype,
                String encoding,
                boolean embedded,
                boolean damaged,
                boolean vertical,
                boolean toUnicodePresent,
                boolean subset
        ) {
            this.name = name;
            this.subtype = subtype;
            this.encoding = encoding;
            this.embedded = embedded;
            this.damaged = damaged;
            this.vertical = vertical;
            this.toUnicodePresent = toUnicodePresent;
            this.subset = subset;
        }

        private FontAudit freeze() {
            return new FontAudit(
                    name,
                    subtype,
                    encoding,
                    embedded,
                    damaged,
                    vertical,
                    toUnicodePresent,
                    subset,
                    glyphCount,
                    rawUnmappedGlyphCount);
        }
    }
}
