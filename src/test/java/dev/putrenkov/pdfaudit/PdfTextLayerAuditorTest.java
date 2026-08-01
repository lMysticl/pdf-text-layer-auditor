package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PdfTextLayerAuditorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void distinguishesTextPageFromBlankPage() throws IOException {
        Path pdf = temporaryDirectory.resolve("mixed.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage textPage = new PDPage();
            document.addPage(textPage);
            try (PDPageContentStream content = new PDPageContentStream(document, textPage)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Searchable text");
                content.endText();
            }

            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);

        assertEquals(2, report.pageCount());
        assertEquals(2, report.pages().size());
        assertFalse(report.pages().get(0).needsAttention());
        assertTrue(report.pages().get(0).glyphCount() > 0);
        assertEquals(
                java.util.List.of(Finding.NO_TEXT_LAYER),
                report.pages().get(1).findings());
        assertEquals(PageClassification.NATIVE_TEXT, report.pages().get(0).classification());
        assertEquals(PageClassification.BLANK, report.pages().get(1).classification());
    }

    @Test
    void classifiesVectorImageNativeMixedAndSparseOcrPages() throws IOException {
        Path pdf = temporaryDirectory.resolve("page-classification.pdf");
        try (PDDocument document = new PDDocument()) {
            PDRectangle size = PDRectangle.LETTER;
            PDPage vector = new PDPage(size);
            document.addPage(vector);
            try (PDPageContentStream content = new PDPageContentStream(document, vector)) {
                content.addRect(72, 72, 100, 100);
                content.fill();
                content.moveTo(10, 10);
                content.lineTo(20, 20);
                content.curveTo(25, 30, 35, 40, 45, 50);
                content.closePath();
                content.stroke();
                content.moveTo(50, 50);
                content.curveTo2(55, 60, 65, 70);
                content.lineTo(75, 50);
                content.fillAndStroke();
                PDShadingType2 shading = new PDShadingType2(new COSDictionary());
                content.shadingFill(shading);
            }

            BufferedImage pixel = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            var image = LosslessFactory.createFromImage(document, pixel);
            PDOptionalContentGroup optionalContent =
                    new PDOptionalContentGroup("test layer");
            image.setOptionalContent(optionalContent);
            PDPage imageOnly = new PDPage(size);
            document.addPage(imageOnly);
            try (PDPageContentStream content = new PDPageContentStream(document, imageOnly)) {
                content.beginMarkedContent(COSName.OC, optionalContent);
                content.drawImage(image, 0, 0, size.getWidth(), size.getHeight());
                content.endMarkedContent();
            }
            PDAnnotationWidget widget = new PDAnnotationWidget();
            widget.setOptionalContent(optionalContent);
            imageOnly.setAnnotations(List.of(widget));

            PDPage nativeText = new PDPage(size);
            document.addPage(nativeText);
            addHelveticaText(document, nativeText, "native text", 72, 720);

            PDPage mixed = new PDPage(size);
            document.addPage(mixed);
            try (PDPageContentStream content = new PDPageContentStream(document, mixed)) {
                content.drawImage(image, 72, 600, 72, 72);
            }
            addHelveticaText(document, mixed, "text plus image", 72, 720);

            PDPage sparseOcr = new PDPage(size);
            document.addPage(sparseOcr);
            try (PDPageContentStream content = new PDPageContentStream(document, sparseOcr)) {
                content.drawImage(image, 0, 0, size.getWidth(), size.getHeight());
            }
            addHelveticaText(document, sparseOcr, "few", 72, 720);

            PDPage tiledSparseOcr = new PDPage(size);
            document.addPage(tiledSparseOcr);
            try (PDPageContentStream content =
                    new PDPageContentStream(document, tiledSparseOcr)) {
                content.drawImage(image, 0, 0, size.getWidth(), size.getHeight() / 2);
                content.drawImage(
                        image,
                        0,
                        size.getHeight() / 2,
                        size.getWidth(),
                        size.getHeight() / 2);
            }
            addHelveticaText(document, tiledSparseOcr, "few", 72, 720);
            document.save(pdf.toFile());
        }

        List<PageAudit> pages = new PdfTextLayerAuditor().audit(pdf).pages();

        assertEquals(PageClassification.VECTOR_ONLY, pages.get(0).classification());
        assertEquals(PageClassification.IMAGE_ONLY, pages.get(1).classification());
        assertEquals(PageClassification.NATIVE_TEXT, pages.get(2).classification());
        assertEquals(PageClassification.MIXED, pages.get(3).classification());
        assertEquals(PageClassification.SPARSE_OCR, pages.get(4).classification());
        assertEquals(PageClassification.SPARSE_OCR, pages.get(5).classification());
        assertTrue(pages.get(4).findings()
                .contains(Finding.SPARSE_TEXT_OVER_FULL_PAGE_IMAGE));
        assertEquals(1, pages.get(1).visualContent().imageCount());
        assertTrue(pages.get(1).visualContent().maxImageCoverageRatio() > 0.99);
        assertTrue(pages.get(1).visualContent().combinedImageCoverageRatio() > 0.99);
        assertTrue(pages.get(5).visualContent().maxImageCoverageRatio() < 0.51);
        assertTrue(pages.get(5).visualContent().combinedImageCoverageRatio() > 0.99);
        assertTrue(pages.get(0).visualContent().paintedVectorPathCount() >= 4);
        assertEquals(1, pages.get(1).visualContent().annotationCount());
        assertEquals(1, pages.get(1).visualContent().widgetAnnotationCount());
        assertTrue(pages.get(1).visualContent().optionalContentPresent());
    }

    @Test
    void measuresInvisibleTransparentOffPageClippedRotatedAndDuplicateGlyphs()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("visibility.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                showHelvetica(content, "A", 72, 720);
                showHelvetica(content, "A", 72, 720);

                content.setRenderingMode(RenderingMode.NEITHER);
                showHelvetica(content, "B", 90, 720);
                content.setRenderingMode(RenderingMode.FILL);

                PDExtendedGraphicsState transparent = new PDExtendedGraphicsState();
                transparent.setNonStrokingAlphaConstant(0f);
                content.setGraphicsStateParameters(transparent);
                showHelvetica(content, "C", 110, 720);
                PDExtendedGraphicsState opaque = new PDExtendedGraphicsState();
                opaque.setNonStrokingAlphaConstant(1f);
                content.setGraphicsStateParameters(opaque);

                showHelvetica(content, "D", -1000, -1000);

                content.addRect(0, 0, 10, 10);
                content.clip();
                showHelvetica(content, "E", 200, 200);
            }
            document.save(pdf.toFile());
        }

        GeometryVisibilityAudit geometry =
                new PdfTextLayerAuditor().audit(pdf).pages().getFirst().geometryVisibility();

        assertTrue(geometry.assessed());
        assertEquals(1, geometry.invisibleGlyphCount());
        assertEquals(1, geometry.transparentGlyphCount());
        assertEquals(1, geometry.offPageGlyphCount());
        assertEquals(1, geometry.clippedGlyphCount());
        assertEquals(1, geometry.duplicateOverlapGlyphCount());
        assertEquals(0, geometry.verticalGlyphCount());
    }

    @Test
    void rejectsFilesAboveConfiguredLimit() throws IOException {
        Path file = temporaryDirectory.resolve("oversized.pdf");
        java.nio.file.Files.writeString(file, "not really a PDF");

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PdfTextLayerAuditor(2, 10).audit(file));

        assertTrue(exception.getMessage().contains("size limit"));
    }

    @Test
    void rejectsDocumentWithoutPages() throws IOException {
        Path pdf = temporaryDirectory.resolve("empty.pdf");
        try (PDDocument document = new PDDocument()) {
            document.save(pdf.toFile());
        }

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PdfTextLayerAuditor().audit(pdf));

        assertEquals("PDF contains no pages", exception.getMessage());
    }

    @Test
    void reportsUnnamedFontWithoutCrashing() throws IOException {
        Path pdf = temporaryDirectory.resolve("unnamed-font.pdf");
        try (PDDocument document = new PDDocument()) {
            addTwoFontPage(document, createType3Font(document, null));
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);

        assertEquals(
                List.of("<unnamed>", "Helvetica"),
                report.pages().getFirst().fonts().stream().map(FontAudit::name).toList());
    }

    @Test
    void keepsDifferentFontStatesWithSameNameSeparate() throws IOException {
        Path pdf = temporaryDirectory.resolve("same-name-fonts.pdf");
        try (PDDocument document = new PDDocument()) {
            addTwoFontPage(document, createType3Font(document, "Helvetica"));
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        List<FontAudit> fonts = report.pages().getFirst().fonts();

        assertEquals(2, fonts.size());
        assertEquals(List.of("Helvetica", "Helvetica"), fonts.stream().map(FontAudit::name).toList());
        assertEquals(List.of(false, true), fonts.stream().map(FontAudit::embedded).toList());
    }

    @Test
    void auditsReadableEmbeddedType0TextAcrossLatinGreekAndCyrillic()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("embedded-type0-unicode.pdf");
        String text = "Zażółć gęślą jaźń | Ελληνικά Ω | Кириллица Ж";
        try (PDDocument document = new PDDocument();
                InputStream input = PDTrueTypeFont.class.getResourceAsStream(
                        "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            if (input == null) {
                throw new IllegalStateException("PDFBox Unicode test font is unavailable.");
            }
            PDPage page = new PDPage();
            document.addPage(page);
            PDType0Font font = PDType0Font.load(document, input);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 16);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        assertFalse(page.needsAttention());
        assertEquals(text.codePointCount(0, text.length()), page.unicodeCharacterCount());
        assertTrue(page.fonts().getFirst().embedded());
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            assertTrue(new PDFTextStripper().getText(document).contains(text));
            assertTrue(countNonWhitePixels(
                    new PDFRenderer(document).renderImageWithDPI(0, 96)) > 500);
        }
    }

    @Test
    void appliesConfiguredTinyTextThreshold() throws IOException {
        Path pdf = temporaryDirectory.resolve("small-text.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        2.5f);
                content.newLineAtOffset(72, 720);
                content.showText("small");
                content.endText();
            }
            document.save(pdf.toFile());
        }

        AuditReport defaultReport = new PdfTextLayerAuditor().audit(pdf);
        AuditReport lowerThresholdReport = new PdfTextLayerAuditor(
                PdfTextLayerAuditor.DEFAULT_MAX_FILE_SIZE_BYTES,
                PdfTextLayerAuditor.DEFAULT_MAX_PAGE_COUNT,
                2.0f)
                .audit(pdf);
        AuditReport disabledReport = new PdfTextLayerAuditor(
                PdfTextLayerAuditor.DEFAULT_MAX_FILE_SIZE_BYTES,
                PdfTextLayerAuditor.DEFAULT_MAX_PAGE_COUNT,
                0.0f)
                .audit(pdf);

        assertTrue(defaultReport.pages().getFirst().findings().contains(Finding.TINY_TEXT));
        assertFalse(lowerThresholdReport.pages().getFirst().findings().contains(Finding.TINY_TEXT));
        assertFalse(disabledReport.pages().getFirst().findings().contains(Finding.TINY_TEXT));
        assertEquals(2.0f, lowerThresholdReport.tinyTextThresholdPoints());
    }

    @Test
    void flagsControlOnlyUnicodeMappingAsMissingUnicode() throws IOException {
        Path pdf = temporaryDirectory.resolve("control-unicode.pdf");
        try (PDDocument document = new PDDocument()) {
            PDType3Font font = createType3Font(document, "ControlMapped");
            font.getCOSObject().setItem(
                    COSName.TO_UNICODE,
                    createToUnicodeCMap(document, "<41> <0001>"));
            addType3TextPage(document, font);
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        assertEquals(1, page.glyphCount());
        assertEquals(1, page.unicodeCharacterCount());
        assertEquals(1, page.missingUnicodeGlyphCount());
        assertEquals(List.of(Finding.MISSING_UNICODE), page.findings());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validUnicodeMappings")
    void acceptsValidUnicodeAcrossScriptsMarksDirectionsAndSymbols(
            String label,
            String destinationHex,
            int expectedCodePoints
    ) throws IOException {
        Path pdf = temporaryDirectory.resolve("valid-" + label + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDType3Font font = createType3Font(document, "ValidUnicode");
            font.getCOSObject().setItem(
                    COSName.TO_UNICODE,
                    createToUnicodeCMap(document, "<41> <" + destinationHex + ">"));
            addType3TextPage(document, font);
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        assertEquals(1, page.glyphCount());
        assertEquals(expectedCodePoints, page.unicodeCharacterCount());
        assertEquals(0, page.missingUnicodeGlyphCount());
        assertFalse(page.findings().contains(Finding.MISSING_UNICODE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUnicodeMappings")
    void rejectsNonSemanticUnicodeMappings(String label, String destinationHex)
            throws IOException {
        Path pdf = temporaryDirectory.resolve("invalid-" + label + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDType3Font font = createType3Font(document, "InvalidUnicode");
            font.getCOSObject().setItem(
                    COSName.TO_UNICODE,
                    createToUnicodeCMap(document, "<41> <" + destinationHex + ">"));
            addType3TextPage(document, font);
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        assertEquals(1, page.glyphCount());
        assertEquals(1, page.missingUnicodeGlyphCount());
        assertTrue(page.findings().contains(Finding.MISSING_UNICODE));
    }

    @Test
    void treatsActualTextAsSemanticEvidenceWithoutHidingRawMappingProvenance()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("actual-text.pdf");
        try (PDDocument document = new PDDocument()) {
            PDType3Font font = createUnmappedType3Font(document);
            addActualTextPage(document, font, "0915");
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        PageAudit page = report.pages().getFirst();

        assertEquals(1, page.glyphCount());
        assertEquals(1, page.unicodeCharacterCount());
        assertEquals(0, page.missingUnicodeGlyphCount());
        assertEquals(1, page.textSurfaces().actualTextGlyphCount());
        assertEquals(1, page.textSurfaces().actualTextCharacterCount());
        assertEquals(1, page.semanticMapping().rawUnmappedGlyphCount());
        assertEquals(1, page.semanticMapping().actualTextResolvedGlyphCount());
        assertFalse(page.findings().contains(Finding.MISSING_UNICODE));
        assertTrue(report.parseHealth().complete());
    }

    @Test
    void convertsMalformedDeclaredToUnicodeIntoTypedEvidence() throws IOException {
        Path pdf = temporaryDirectory.resolve("malformed-tounicode.pdf");
        try (PDDocument document = new PDDocument()) {
            PDType3Font font = createType3Font(document, "MalformedMap");
            COSStream malformed = document.getDocument().createCOSStream();
            try (var output = malformed.createOutputStream()) {
                output.write("1 beginbfchar <41> <00G1> endbfchar"
                        .getBytes(StandardCharsets.US_ASCII));
            }
            font.getCOSObject().setItem(COSName.TO_UNICODE, malformed);
            addType3TextPage(document, font);
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        PageAudit page = report.pages().getFirst();

        assertTrue(report.parseHealth().recovered());
        assertEquals(
                List.of(ParseDiagnosticCode.MALFORMED_TOUNICODE_CMAP),
                report.parseHealth().diagnostics().stream()
                        .map(ParseDiagnostic::code)
                        .toList());
        assertEquals(1, page.semanticMapping().malformedToUnicodeFontCount());
        assertTrue(page.findings().contains(Finding.MALFORMED_TOUNICODE_CMAP));
    }

    @Test
    void detectsWhenContentStreamAndPositionReadingOrdersDiverge() throws IOException {
        Path pdf = temporaryDirectory.resolve("out-of-order.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(100, 720);
                content.showText("B");
                content.endText();
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("A");
                content.endText();
            }
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        assertTrue(page.readingOrder().assessed());
        assertTrue(page.readingOrder().diverges());
        assertEquals(2, page.readingOrder().streamCharacterCount());
        assertEquals(2, page.readingOrder().positionCharacterCount());
        assertTrue(page.findings().contains(Finding.READING_ORDER_DIVERGENCE));
    }

    @Test
    void flagsTextStripperFallbackWhenFontHasNoUnicodeMapping() throws IOException {
        Path pdf = temporaryDirectory.resolve("fallback-unicode.pdf");
        try (PDDocument document = new PDDocument()) {
            addType3TextPage(document, createUnmappedType3Font(document));
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        assertEquals(1, page.glyphCount());
        assertEquals(1, page.missingUnicodeGlyphCount());
        assertEquals(List.of(Finding.MISSING_UNICODE), page.findings());
    }

    @Test
    void continuesAfterType0FontWithoutDescendantFont() throws IOException {
        Path pdf = temporaryDirectory.resolve("missing-descendant-font.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDResources resources = new PDResources();
            page.setResources(resources);

            COSDictionary malformedFont = new COSDictionary();
            malformedFont.setItem(COSName.TYPE, COSName.FONT);
            malformedFont.setItem(COSName.SUBTYPE, COSName.TYPE0);
            malformedFont.setItem(COSName.BASE_FONT, COSName.getPDFName("MalformedType0"));
            malformedFont.setItem(COSName.ENCODING, COSName.IDENTITY_H);
            COSDictionary fonts = new COSDictionary();
            fonts.setItem(COSName.getPDFName("F1"), malformedFont);
            resources.getCOSObject().setItem(COSName.FONT, fonts);

            PDStream content = new PDStream(document);
            try (var output = content.createOutputStream()) {
                output.write(
                        "BT /F1 12 Tf 72 720 Td <41> Tj ET"
                                .getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(content);
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        assertEquals(1, page.glyphCount());
        assertEquals(1, page.missingUnicodeGlyphCount());
        assertEquals(List.of(Finding.MISSING_UNICODE), page.findings());
        assertEquals("<malformed-font>", page.fonts().getFirst().name());
        assertTrue(page.fonts().getFirst().damaged());
    }

    @Test
    void rejectsInvalidTinyTextThreshold() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfTextLayerAuditor(100, 10, -1.0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfTextLayerAuditor(100, 10, Float.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfTextLayerAuditor(100, 10, Float.POSITIVE_INFINITY));
    }

    @Test
    void auditsOnlySelectedPages() throws IOException {
        Path pdf = temporaryDirectory.resolve("selected-pages.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage unselectedTextPage = new PDPage();
            document.addPage(unselectedTextPage);
            try (PDPageContentStream content =
                    new PDPageContentStream(document, unselectedTextPage)) {
                content.beginText();
                content.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12);
                content.newLineAtOffset(72, 720);
                content.showText("not selected");
                content.endText();
            }

            PDPage textPage = new PDPage();
            document.addPage(textPage);
            try (PDPageContentStream content = new PDPageContentStream(document, textPage)) {
                content.beginText();
                content.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12);
                content.newLineAtOffset(72, 720);
                content.showText("selected");
                content.endText();
            }

            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        AuditReport report =
                new PdfTextLayerAuditor().audit(pdf, PageSelection.parse("2"));

        assertEquals(3, report.pageCount());
        assertEquals(1, report.pages().size());
        assertEquals(2, report.pages().getFirst().pageNumber());
        assertFalse(report.pages().getFirst().needsAttention());
    }

    @Test
    void rejectsSelectedPageOutsideDocument() throws IOException {
        Path pdf = temporaryDirectory.resolve("one-page.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PdfTextLayerAuditor().audit(pdf, PageSelection.parse("2")));

        assertEquals(
                "Requested page 2 exceeds document page count of 1",
                exception.getMessage());
    }

    @Test
    void auditsEncryptedPdfWhenExtractionIsAllowed() throws IOException {
        Path pdf = createEncryptedPdf("allowed.pdf", "", true);

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);

        assertTrue(report.encrypted());
        assertTrue(report.extractionAllowed());
    }

    @Test
    void rejectsPasswordProtectedPdfWithoutPassword() throws IOException {
        Path pdf = createEncryptedPdf("password.pdf", "user-password", true);

        assertThrows(
                InvalidPasswordException.class,
                () -> new PdfTextLayerAuditor().audit(pdf));
    }

    @Test
    void rejectsPdfWhenExtractionPermissionIsDisabled() throws IOException {
        Path pdf = createEncryptedPdf("restricted.pdf", "", false);

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> new PdfTextLayerAuditor().audit(pdf));

        assertEquals("PDF permissions do not allow text extraction", exception.getMessage());
    }

    private Path createEncryptedPdf(
            String fileName,
            String userPassword,
            boolean extractionAllowed
    ) throws IOException {
        Path pdf = temporaryDirectory.resolve(fileName);
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            AccessPermission permissions = new AccessPermission();
            permissions.setCanExtractContent(extractionAllowed);
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-password", userPassword, permissions);
            policy.setEncryptionKeyLength(128);
            policy.setPreferAES(true);
            document.protect(policy);
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private static void addTwoFontPage(PDDocument document, PDType3Font type3Font)
            throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        page.setResources(new PDResources());
        page.getResources().put(COSName.getPDFName("F1"), type3Font);
        page.getResources().put(
                COSName.getPDFName("F2"),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA));

        PDStream pageContent = new PDStream(document);
        try (var output = pageContent.createOutputStream()) {
            output.write(
                    ("BT /F1 12 Tf 72 720 Td <41> Tj ET "
                                    + "BT /F2 12 Tf 90 720 Td (B) Tj ET")
                            .getBytes(StandardCharsets.US_ASCII));
        }
        page.setContents(pageContent);
    }

    private static void addType3TextPage(PDDocument document, PDType3Font font)
            throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        page.setResources(new PDResources());
        page.getResources().put(COSName.getPDFName("F1"), font);

        PDStream pageContent = new PDStream(document);
        try (var output = pageContent.createOutputStream()) {
            output.write(
                    "BT /F1 12 Tf 72 720 Td <41> Tj ET"
                            .getBytes(StandardCharsets.US_ASCII));
        }
        page.setContents(pageContent);
    }

    private static void addHelveticaText(
            PDDocument document,
            PDPage page,
            String text,
            float x,
            float y
    ) throws IOException {
        try (PDPageContentStream content = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true)) {
            showHelvetica(content, text, x, y);
        }
    }

    private static void showHelvetica(
            PDPageContentStream content,
            String text,
            float x,
            float y
    ) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static void addActualTextPage(
            PDDocument document,
            PDType3Font font,
            String actualTextUtf16Hex
    ) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        page.setResources(new PDResources());
        page.getResources().put(COSName.getPDFName("F1"), font);

        PDStream pageContent = new PDStream(document);
        try (var output = pageContent.createOutputStream()) {
            output.write(("/Span << /ActualText <FEFF" + actualTextUtf16Hex
                            + ">> BDC BT /F1 12 Tf 72 720 Td <41> Tj ET EMC")
                    .getBytes(StandardCharsets.US_ASCII));
        }
        page.setContents(pageContent);
    }

    private static COSStream createToUnicodeCMap(PDDocument document, String mapping)
            throws IOException {
        String cmap = """
                /CIDInit /ProcSet findresource begin
                12 dict begin
                begincmap
                /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
                /CMapName /ControlMap def
                /CMapType 2 def
                1 begincodespacerange
                <00> <FF>
                endcodespacerange
                1 beginbfchar
                %s
                endbfchar
                endcmap
                CMapName currentdict /CMap defineresource pop
                end
                end
                """.formatted(mapping);
        COSStream stream = document.getDocument().createCOSStream();
        try (var output = stream.createOutputStream()) {
            output.write(cmap.getBytes(StandardCharsets.US_ASCII));
        }
        return stream;
    }

    private static Stream<Arguments> validUnicodeMappings() {
        return Stream.of(
                Arguments.of("latin", "0041", 1),
                Arguments.of("latin-diacritic-nfc", "0105", 1),
                Arguments.of("latin-diacritic-nfd", "00650301", 2),
                Arguments.of("cyrillic", "0416", 1),
                Arguments.of("greek", "03A9", 1),
                Arguments.of("hebrew", "05E9", 1),
                Arguments.of("arabic", "0634", 1),
                Arguments.of("devanagari", "0915", 1),
                Arguments.of("bengali", "0995", 1),
                Arguments.of("thai", "0E01", 1),
                Arguments.of("han", "6F22", 1),
                Arguments.of("hiragana", "3042", 1),
                Arguments.of("katakana", "30A2", 1),
                Arguments.of("hangul", "D55C", 1),
                Arguments.of("math", "2211", 1),
                Arguments.of("currency", "20AC", 1),
                Arguments.of("dingbat", "2610", 1),
                Arguments.of("combining-mark", "0301", 1),
                Arguments.of("right-to-left-mark", "200F", 1),
                Arguments.of("emoji", "D83DDE00", 1),
                Arguments.of("emoji-zwj-sequence", "D83DDC69200DD83DDCBB", 3),
                Arguments.of("variation-sequence", "2764FE0F", 2)
        );
    }

    private static Stream<Arguments> invalidUnicodeMappings() {
        return Stream.of(
                Arguments.of("nul", "0000"),
                Arguments.of("control", "0001"),
                Arguments.of("replacement", "FFFD"),
                Arguments.of("private-use", "E000"),
                Arguments.of("unassigned", "0378"),
                Arguments.of("noncharacter-plane", "FDD0"),
                Arguments.of("noncharacter-bmp-end", "FFFE"),
                Arguments.of("noncharacter-max", "DBDFFFFF")
        );
    }

    private static int countNonWhitePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) != 0x00FFFFFF) {
                    count++;
                }
            }
        }
        return count;
    }

    private static PDType3Font createType3Font(PDDocument document, String name)
            throws IOException {
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.TYPE, COSName.FONT);
        dictionary.setItem(COSName.SUBTYPE, COSName.TYPE3);
        if (name != null) {
            dictionary.setItem(COSName.NAME, COSName.getPDFName(name));
        }
        dictionary.setItem(
                COSName.FONT_BBOX,
                new PDRectangle(0, 0, 600, 700).getCOSArray());
        dictionary.setItem(
                COSName.FONT_MATRIX,
                new Matrix(0.001f, 0, 0, 0.001f, 0, 0).toCOSArray());
        dictionary.setInt(COSName.FIRST_CHAR, 65);
        dictionary.setInt(COSName.LAST_CHAR, 65);
        COSArray widths = new COSArray();
        widths.add(new COSFloat(600));
        dictionary.setItem(COSName.WIDTHS, widths);
        dictionary.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING);

        COSDictionary characterProcedures = new COSDictionary();
        COSStream characterStream = document.getDocument().createCOSStream();
        try (var output = characterStream.createOutputStream()) {
            output.write(
                    "0 0 600 700 d1 0 0 600 700 re f"
                            .getBytes(StandardCharsets.US_ASCII));
        }
        characterProcedures.setItem(COSName.getPDFName("A"), characterStream);
        dictionary.setItem(COSName.CHAR_PROCS, characterProcedures);
        return new PDType3Font(dictionary);
    }

    private static PDType3Font createUnmappedType3Font(PDDocument document)
            throws IOException {
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.TYPE, COSName.FONT);
        dictionary.setItem(COSName.SUBTYPE, COSName.TYPE3);
        dictionary.setItem(
                COSName.FONT_BBOX,
                new PDRectangle(0, 0, 600, 700).getCOSArray());
        dictionary.setItem(
                COSName.FONT_MATRIX,
                new Matrix(0.001f, 0, 0, 0.001f, 0, 0).toCOSArray());
        dictionary.setInt(COSName.FIRST_CHAR, 65);
        dictionary.setInt(COSName.LAST_CHAR, 65);
        COSArray widths = new COSArray();
        widths.add(new COSFloat(600));
        dictionary.setItem(COSName.WIDTHS, widths);

        COSDictionary encoding = new COSDictionary();
        encoding.setItem(COSName.BASE_ENCODING, COSName.WIN_ANSI_ENCODING);
        COSArray differences = new COSArray();
        differences.add(COSInteger.get(65));
        differences.add(COSName.getPDFName("integraldisplay"));
        encoding.setItem(COSName.DIFFERENCES, differences);
        dictionary.setItem(COSName.ENCODING, encoding);

        COSDictionary characterProcedures = new COSDictionary();
        COSStream characterStream = document.getDocument().createCOSStream();
        try (var output = characterStream.createOutputStream()) {
            output.write(
                    "0 0 600 700 d1 0 0 600 700 re f"
                            .getBytes(StandardCharsets.US_ASCII));
        }
        characterProcedures.setItem(
                COSName.getPDFName("integraldisplay"),
                characterStream);
        dictionary.setItem(COSName.CHAR_PROCS, characterProcedures);
        return new PDType3Font(dictionary);
    }
}
