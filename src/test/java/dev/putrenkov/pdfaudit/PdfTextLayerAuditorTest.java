package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.awt.BasicStroke;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentMembershipDictionary;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
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

            PDPage partialOcr = new PDPage(size);
            document.addPage(partialOcr);
            try (PDPageContentStream content =
                    new PDPageContentStream(document, partialOcr)) {
                content.drawImage(image, 0, 0, size.getWidth(), size.getHeight());
            }
            addHelveticaText(
                    document,
                    partialOcr,
                    "long header text that exceeds the old character threshold but covers one band",
                    36,
                    720);

            PDPage denseImageMosaic = new PDPage(size);
            document.addPage(denseImageMosaic);
            try (PDPageContentStream content =
                    new PDPageContentStream(document, denseImageMosaic)) {
                float tileWidth = size.getWidth() / 9;
                float tileHeight = size.getHeight() / 9;
                for (int row = 0; row < 9; row++) {
                    for (int column = 0; column < 9; column++) {
                        content.drawImage(
                                image,
                                column * tileWidth,
                                row * tileHeight,
                                tileWidth,
                                tileHeight);
                    }
                }
            }
            document.save(pdf.toFile());
        }

        List<PageAudit> pages = new PdfTextLayerAuditor().audit(pdf).pages();

        assertEquals(PageClassification.VECTOR_ONLY, pages.get(0).classification());
        assertEquals(PageClassification.IMAGE_ONLY, pages.get(1).classification());
        assertEquals(PageClassification.NATIVE_TEXT, pages.get(2).classification());
        assertEquals(PageClassification.MIXED, pages.get(3).classification());
        assertEquals(PageClassification.SPARSE_OCR, pages.get(4).classification());
        assertEquals(PageClassification.SPARSE_OCR, pages.get(5).classification());
        assertEquals(PageClassification.PARTIAL_OCR, pages.get(6).classification());
        assertEquals(PageClassification.IMAGE_ONLY, pages.get(7).classification());
        assertTrue(pages.get(4).findings()
                .contains(Finding.SPARSE_TEXT_OVER_FULL_PAGE_IMAGE));
        assertTrue(pages.get(6).findings()
                .contains(Finding.PARTIAL_TEXT_OVER_FULL_PAGE_IMAGE));
        assertEquals(1, pages.get(1).visualContent().imageCount());
        assertTrue(pages.get(1).visualContent().maxImageCoverageRatio() > 0.99);
        assertTrue(pages.get(1).visualContent().combinedImageCoverageRatio() > 0.99);
        assertTrue(pages.get(5).visualContent().maxImageCoverageRatio() < 0.51);
        assertTrue(pages.get(5).visualContent().combinedImageCoverageRatio() > 0.99);
        assertEquals(64, pages.get(6).visualContent().imageOccupiedGridCellCount());
        assertTrue(pages.get(6).visualContent().imageTextOverlapGridCellCount() > 0);
        assertTrue(pages.get(6).visualContent().imageTextOverlapRatio() < 0.25);
        assertEquals(81, pages.get(7).visualContent().imageCount());
        assertTrue(pages.get(7).visualContent().maxImageCoverageRatio() < 0.02);
        assertTrue(pages.get(7).visualContent().combinedImageCoverageRatio() > 0.99);
        assertEquals(81, pages.get(7).spatialEvidence()
                .visualRegions().totalRegionCount());
        assertEquals(32, pages.get(7).spatialEvidence()
                .visualRegions().regions().size());
        assertTrue(pages.get(7).spatialEvidence()
                .visualRegions().regionsTruncated());
        assertTrue(pages.get(0).visualContent().paintedVectorPathCount() >= 4);
        assertEquals(1, pages.get(1).visualContent().annotationCount());
        assertEquals(1, pages.get(1).visualContent().widgetAnnotationCount());
        assertTrue(pages.get(1).visualContent().optionalContentPresent());
    }

    @Test
    void recordsImageRegionsInTopLeftDisplayCoordinatesForEveryPageRotation()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("image-spatial-evidence.pdf");
        try (PDDocument document = new PDDocument()) {
            BufferedImage pixel = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            var image = LosslessFactory.createFromImage(document, pixel);
            for (int rotation : List.of(0, 90, 180, 270)) {
                PDRectangle crop = new PDRectangle(10, 20, 200, 100);
                PDPage page = new PDPage(crop);
                page.setRotation(rotation);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 40, 50, 20, 10);
                }
            }
            document.save(pdf.toFile());
        }

        List<PageAudit> pages = new PdfTextLayerAuditor().audit(pdf).pages();

        assertImageRegion(pages.get(0), 0, 200, 100, 30, 60, 20, 10);
        assertImageRegion(pages.get(1), 90, 100, 200, 30, 30, 10, 20);
        assertImageRegion(pages.get(2), 180, 200, 100, 150, 30, 20, 10);
        assertImageRegion(pages.get(3), 270, 100, 200, 60, 150, 10, 20);
    }

    @Test
    void recordsVisibleAnnotationAndFormRegionsWithoutImageSampleStarvation()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("typed-object-regions.pdf");
        try (PDDocument document = new PDDocument()) {
            PDRectangle crop = new PDRectangle(10, 20, 200, 100);
            PDPage page = new PDPage(crop);
            page.setRotation(90);
            document.addPage(page);
            PDOptionalContentGroup off = new PDOptionalContentGroup("off");
            PDOptionalContentProperties properties = new PDOptionalContentProperties();
            properties.addGroup(off);
            properties.setGroupEnabled(off, false);
            document.getDocumentCatalog().setOCProperties(properties);
            BufferedImage pixel = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            var image = LosslessFactory.createFromImage(document, pixel);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int index = 0; index < 33; index++) {
                    content.drawImage(image, 40, 50, 20, 10);
                }
            }
            PDAnnotationText annotation = new PDAnnotationText();
            annotation.setRectangle(new PDRectangle(60, 70, 30, 15));
            annotation.setContents("do-not-retain-annotation-content");
            PDAnnotationWidget widget = new PDAnnotationWidget();
            widget.setRectangle(new PDRectangle(100, 80, 40, 10));
            PDAnnotationText hidden = new PDAnnotationText();
            hidden.setRectangle(new PDRectangle(20, 30, 10, 10));
            hidden.setHidden(true);
            PDAnnotationText invisible = new PDAnnotationText();
            invisible.setRectangle(new PDRectangle(30, 40, 10, 10));
            invisible.setInvisible(true);
            PDAnnotationText noView = new PDAnnotationText();
            noView.setRectangle(new PDRectangle(40, 50, 10, 10));
            noView.setNoView(true);
            PDAnnotationText outsideCrop = new PDAnnotationText();
            outsideCrop.setRectangle(new PDRectangle(500, 500, 10, 10));
            PDAnnotationText noRectangle = new PDAnnotationText();
            PDAnnotationText optionalHidden = new PDAnnotationText();
            optionalHidden.setRectangle(new PDRectangle(50, 60, 10, 10));
            optionalHidden.setOptionalContent(off);
            page.setAnnotations(List.of(
                    annotation,
                    widget,
                    hidden,
                    invisible,
                    noView,
                    outsideCrop,
                    noRectangle,
                    optionalHidden));
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        PageAudit page = report.pages().getFirst();
        VisualRegionAudit visualRegions = page.spatialEvidence().visualRegions();

        assertEquals(35, visualRegions.totalRegionCount());
        assertEquals(33, visualRegions.counts().imageCount());
        assertEquals(1, visualRegions.counts().annotationCount());
        assertEquals(1, visualRegions.counts().formFieldCount());
        assertEquals(34, visualRegions.regions().size());
        assertTrue(visualRegions.regionsTruncated());
        assertRegion(visualRegions.regions().get(32),
                VisualRegionType.ANNOTATION, 50, 50, 15, 30);
        assertRegion(visualRegions.regions().get(33),
                VisualRegionType.FORM_FIELD, 60, 90, 10, 40);
        assertEquals(8, page.visualContent().annotationCount());
        assertEquals(1, page.visualContent().widgetAnnotationCount());
        assertEquals(1, page.optionalContent().hiddenInViewReferenceCount());
        assertFalse(JsonReportPrinter.toJson(report)
                .contains("do-not-retain-annotation-content"));
    }

    @Test
    void recordsOnlyLocatablePaintedVectorPathRegionsWithExactPerTypeCounts()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("vector-path-regions.pdf");
        try (PDDocument document = new PDDocument()) {
            PDRectangle crop = new PDRectangle(10, 20, 200, 100);
            PDPage bounded = new PDPage(crop);
            bounded.setRotation(90);
            document.addPage(bounded);
            try (PDPageContentStream content = new PDPageContentStream(document, bounded)) {
                content.addRect(30, 40, 20, 10);
                content.fill();

                content.setLineWidth(4);
                content.moveTo(70, 60);
                content.lineTo(90, 60);
                content.stroke();

                content.saveGraphicsState();
                content.addRect(110, 40, 10, 10);
                content.clip();
                content.addRect(105, 35, 30, 30);
                content.fill();
                content.restoreGraphicsState();

                PDExtendedGraphicsState transparent = new PDExtendedGraphicsState();
                transparent.setNonStrokingAlphaConstant(0f);
                content.saveGraphicsState();
                content.setGraphicsStateParameters(transparent);
                content.addRect(140, 40, 10, 10);
                content.fill();
                content.restoreGraphicsState();

                content.addRect(500, 500, 10, 10);
                content.fill();

                content.addRect(130, 80, 0.0001f, 10);
                content.fill();

                content.setLineWidth(2);
                content.addRect(150, 60, 10, 10);
                content.fillAndStroke();

                content.setLineDashPattern(new float[]{0, 0}, 0);
                content.moveTo(30, 90);
                content.lineTo(60, 90);
                content.stroke();

                PDShadingType2 shading = new PDShadingType2(new COSDictionary());
                content.shadingFill(shading);
            }

            PDPage truncated = new PDPage(crop);
            document.addPage(truncated);
            try (PDPageContentStream content = new PDPageContentStream(document, truncated)) {
                for (int index = 0; index < 33; index++) {
                    content.addRect(
                            20 + index % 11 * 15,
                            30 + index / 11 * 20,
                            5,
                            5);
                    content.fill();
                }
            }
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        PageAudit bounded = report.pages().get(0);
        VisualRegionAudit boundedRegions = bounded.spatialEvidence().visualRegions();
        List<VisualRegion> vectorRegions = boundedRegions.regions().stream()
                .filter(region -> region.type().name().equals("VECTOR_PATH"))
                .toList();

        assertEquals(4, boundedRegions.totalRegionCount());
        assertEquals(4, vectorRegions.size());
        assertRegionNamed(vectorRegions.get(0), "VECTOR_PATH", 20, 20, 10, 20);
        assertRegionNamed(vectorRegions.get(1), "VECTOR_PATH", 38, 60, 4, 20);
        assertRegionNamed(vectorRegions.get(2), "VECTOR_PATH", 20, 100, 10, 10);
        assertRegionNamed(vectorRegions.get(3), "VECTOR_PATH", 39, 139, 12, 12);
        assertEquals(9, bounded.visualContent().paintedVectorPathCount());

        PageAudit truncated = report.pages().get(1);
        VisualRegionAudit truncatedRegions = truncated.spatialEvidence().visualRegions();
        assertEquals(33, truncatedRegions.totalRegionCount());
        assertEquals(32, truncatedRegions.regions().stream()
                .filter(region -> region.type().name().equals("VECTOR_PATH"))
                .count());
        assertTrue(truncatedRegions.regionsTruncated());
        assertEquals(33, truncated.visualContent().paintedVectorPathCount());
        assertTrue(JsonReportPrinter.toJson(report).contains("\"vectorPathCount\":33"));
    }

    @Test
    void excludesHiddenOptionalContentAndTransparentPaintFromVisualRegions()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("visible-region-filtering.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200, 200));
            document.addPage(page);

            PDOptionalContentGroup off = new PDOptionalContentGroup("off");
            PDOptionalContentProperties properties = new PDOptionalContentProperties();
            properties.addGroup(off);
            properties.setGroupEnabled(off, false);
            document.getDocumentCatalog().setOCProperties(properties);

            BufferedImage pixel = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            var visibleImage = LosslessFactory.createFromImage(document, pixel);
            var hiddenImage = LosslessFactory.createFromImage(document, pixel);
            hiddenImage.setOptionalContent(off);

            PDFormXObject hiddenForm = new PDFormXObject(new PDStream(document));
            hiddenForm.setBBox(new PDRectangle(20, 20));
            hiddenForm.setResources(new PDResources());
            hiddenForm.setOptionalContent(off);
            try (PDFormContentStream formContent = new PDFormContentStream(hiddenForm)) {
                formContent.addRect(0, 0, 20, 20);
                formContent.fill();
            }

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(visibleImage, 10, 10, 20, 20);

                content.beginMarkedContent(COSName.OC, off);
                content.drawImage(visibleImage, 40, 10, 20, 20);
                content.endMarkedContent();

                content.drawImage(hiddenImage, 70, 10, 20, 20);
                content.saveGraphicsState();
                content.transform(Matrix.getTranslateInstance(70, 100));
                content.drawForm(hiddenForm);
                content.restoreGraphicsState();

                PDExtendedGraphicsState transparent = new PDExtendedGraphicsState();
                transparent.setNonStrokingAlphaConstant(0f);
                content.saveGraphicsState();
                content.setGraphicsStateParameters(transparent);
                content.drawImage(visibleImage, 100, 10, 20, 20);
                content.restoreGraphicsState();

                content.addRect(10, 100, 20, 20);
                content.fill();

                content.beginMarkedContent(COSName.OC, off);
                content.addRect(40, 100, 20, 20);
                content.fill();
                content.endMarkedContent();
            }
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();
        VisualRegionAudit visualRegions = page.spatialEvidence().visualRegions();

        assertEquals(2, visualRegions.totalRegionCount());
        assertEquals(1, visualRegions.counts().imageCount());
        assertEquals(1, visualRegions.counts().vectorPathCount());
        assertEquals(2, visualRegions.regions().size());
    }

    @Test
    void matchesPdfBoxDashNormalizationForVisualStrokeEnvelope() throws IOException {
        Path pdf = temporaryDirectory.resolve("zero-containing-dash.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200, 200));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setLineWidth(2);
                content.setLineDashPattern(new float[]{0, 10}, 0);
                content.moveTo(10, 150);
                content.lineTo(70, 150);
                content.stroke();
            }
            document.save(pdf.toFile());
        }

        VisualRegion actual = new PdfTextLayerAuditor().audit(pdf)
                .pages().getFirst().spatialEvidence().visualRegions().regions().getFirst();
        var expectedBounds = new BasicStroke(
                2,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10,
                new float[]{0.062f, 10},
                0)
                .createStrokedShape(new Line2D.Float(10, 150, 70, 150))
                .getBounds2D();

        assertRegion(
                actual,
                VisualRegionType.VECTOR_PATH,
                expectedBounds.getX(),
                200 - expectedBounds.getMaxY(),
                expectedBounds.getWidth(),
                expectedBounds.getHeight());
    }

    private static void assertImageRegion(
            PageAudit page,
            int rotation,
            double pageWidth,
            double pageHeight,
            double x,
            double y,
            double width,
            double height
    ) {
        SpatialEvidenceAudit spatial = page.spatialEvidence();
        assertTrue(spatial.assessed());
        assertEquals(rotation, spatial.rotationDegrees());
        assertEquals(pageWidth, spatial.pageWidthPoints());
        assertEquals(pageHeight, spatial.pageHeightPoints());
        VisualRegionAudit visualRegions = spatial.visualRegions();
        assertEquals(1, visualRegions.totalRegionCount());
        assertFalse(visualRegions.regionsTruncated());
        VisualRegion region = visualRegions.regions().getFirst();
        assertEquals(VisualRegionType.IMAGE, region.type());
        assertEquals(x, region.xPoints(), 0.001);
        assertEquals(y, region.yPoints(), 0.001);
        assertEquals(width, region.widthPoints(), 0.001);
        assertEquals(height, region.heightPoints(), 0.001);
    }

    private static void assertRegion(
            VisualRegion region,
            VisualRegionType type,
            double x,
            double y,
            double width,
            double height
    ) {
        assertEquals(type, region.type());
        assertEquals(x, region.xPoints(), 0.001);
        assertEquals(y, region.yPoints(), 0.001);
        assertEquals(width, region.widthPoints(), 0.001);
        assertEquals(height, region.heightPoints(), 0.001);
    }

    private static void assertRegionNamed(
            VisualRegion region,
            String type,
            double x,
            double y,
            double width,
            double height
    ) {
        assertEquals(type, region.type().name());
        assertEquals(x, region.xPoints(), 0.001);
        assertEquals(y, region.yPoints(), 0.001);
        assertEquals(width, region.widthPoints(), 0.001);
        assertEquals(height, region.heightPoints(), 0.001);
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
    void auditsAllAnnotationAppearanceStatesAndOptionalContentDestinations()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("annotation-optional-content.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDOptionalContentGroup group = new PDOptionalContentGroup("review layer");
            COSDictionary usage = new COSDictionary();
            COSDictionary view = new COSDictionary();
            view.setItem(COSName.getPDFName("ViewState"), COSName.ON);
            usage.setItem(COSName.getPDFName("View"), view);
            COSDictionary print = new COSDictionary();
            print.setItem(COSName.getPDFName("PrintState"), COSName.OFF);
            usage.setItem(COSName.getPDFName("Print"), print);
            COSDictionary export = new COSDictionary();
            export.setItem(COSName.getPDFName("ExportState"), COSName.ON);
            usage.setItem(COSName.getPDFName("Export"), export);
            group.getCOSObject().setItem(COSName.getPDFName("Usage"), usage);
            PDOptionalContentProperties properties = new PDOptionalContentProperties();
            properties.addGroup(group);
            properties.setGroupEnabled(group, true);
            document.getDocumentCatalog().setOCProperties(properties);

            PDAppearanceStream normal = createAppearance(
                    document,
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    "OK");
            PDAppearanceStream rollover = createUnmappedAppearance(document);
            PDAppearanceDictionary appearances = new PDAppearanceDictionary();
            appearances.setNormalAppearance(normal);
            appearances.setRolloverAppearance(rollover);
            PDAnnotationWidget widget = new PDAnnotationWidget();
            widget.setRectangle(new PDRectangle(72, 680, 120, 30));
            widget.setAppearance(appearances);
            widget.setOptionalContent(group);
            page.setAnnotations(List.of(widget));
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        PageAudit page = report.pages().getFirst();

        assertTrue(report.completeness().annotations());
        assertTrue(report.completeness().optionalContent());
        assertEquals(2, page.annotationAppearances().appearanceStreamCount());
        assertEquals(3, page.annotationAppearances().glyphCount());
        assertEquals(1, page.annotationAppearances().missingUnicodeGlyphCount());
        assertTrue(page.findings().contains(Finding.ANNOTATION_MISSING_UNICODE));
        assertEquals(1, page.optionalContent().referenceCount());
        assertEquals(0, page.optionalContent().hiddenInViewReferenceCount());
        assertEquals(1, page.optionalContent().hiddenInPrintReferenceCount());
        assertEquals(0, page.optionalContent().hiddenInExportReferenceCount());
    }

    @Test
    void evaluatesOptionalContentMembershipPoliciesExpressionsAndFailures()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("optional-membership.pdf");
        try (PDDocument document = new PDDocument()) {
            PDOptionalContentGroup on = new PDOptionalContentGroup("on");
            PDOptionalContentGroup off = new PDOptionalContentGroup("off");
            PDOptionalContentProperties properties = new PDOptionalContentProperties();
            properties.addGroup(on);
            properties.addGroup(off);
            properties.setGroupEnabled(on, true);
            properties.setGroupEnabled(off, false);
            document.getDocumentCatalog().setOCProperties(properties);

            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            List<PDOptionalContentMembershipDictionary> memberships = List.of(
                    membership(List.of(on, off), COSName.ANY_ON, null),
                    membership(List.of(on, off), COSName.ALL_ON, null),
                    membership(List.of(on, off), COSName.ANY_OFF, null),
                    membership(List.of(on, off), COSName.ALL_OFF, null),
                    membership(List.of(), null, expression("And", on, off)),
                    membership(List.of(), null, expression("Or", on, off)),
                    membership(List.of(), null, expression("Not", off)),
                    membership(List.of(), null, new COSArray()));
            memberships.getLast().getCOSObject().setItem(
                    COSName.VE,
                    new COSArray(List.of(COSName.getPDFName("Not"))));

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (PDOptionalContentMembershipDictionary membership : memberships) {
                    content.beginMarkedContent(COSName.OC, membership);
                    content.moveTo(10, 10);
                    content.lineTo(20, 20);
                    content.stroke();
                    content.endMarkedContent();
                }
                PDFormXObject form = new PDFormXObject(new PDStream(document));
                form.setBBox(new PDRectangle(10, 10));
                form.setResources(new PDResources());
                form.setOptionalContent(on);
                content.drawForm(form);
            }
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        OptionalContentAudit optional = report.pages().getFirst().optionalContent();

        assertFalse(optional.complete());
        assertFalse(report.completeness().optionalContent());
        assertEquals(9, optional.referenceCount());
        assertEquals(8, optional.membershipReferenceCount());
        assertEquals(3, optional.evaluationFailureCount());
        assertTrue(optional.hiddenInViewReferenceCount() > 0);
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
        FontAudit font = page.fonts().getFirst();
        assertTrue(font.embedded());
        assertEquals("Type0", font.subtype());
        assertEquals("Identity-H", font.encoding());
        assertFalse(font.vertical());
        assertTrue(font.toUnicodePresent());
        assertTrue(font.subset());
        assertEquals(0, font.rawUnmappedGlyphCount());
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            assertTrue(new PDFTextStripper().getText(document).contains(text));
            assertTrue(countNonWhitePixels(
                    new PDFRenderer(document).renderImageWithDPI(0, 96)) > 500);
        }
    }

    @Test
    void auditsVerticalCjkTextInEmbeddedCffCidFont() throws IOException {
        Path pdf;
        String text = "漢字あア한글";
        try (InputStream input = PdfTextLayerAuditorTest.class.getResourceAsStream(
                "/fonts/vertical-cjk-cff.pdf")) {
            if (input == null) {
                throw new IllegalStateException("Vertical CJK/CFF fixture is unavailable.");
            }
            pdf = temporaryDirectory.resolve("vertical-cjk-cff.pdf");
            java.nio.file.Files.copy(input, pdf);
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        assertEquals(text.codePointCount(0, text.length()), page.glyphCount());
        assertEquals(0, page.missingUnicodeGlyphCount());
        assertTrue(page.geometryVisibility().verticalGlyphCount() > 0);
        FontAudit font = page.fonts().getFirst();
        assertEquals("Type0", font.subtype());
        assertEquals("Identity-V", font.encoding());
        assertTrue(font.embedded());
        assertFalse(font.damaged());
        assertTrue(font.vertical());
        assertTrue(font.toUnicodePresent());
        assertTrue(page.unicodeProfile().scripts().containsAll(
                List.of("HAN", "HIRAGANA", "KATAKANA", "HANGUL")));
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            String extracted = new PDFTextStripper().getText(document)
                    .replaceAll("\\s+", "");
            assertTrue(extracted.contains(text));
            assertTrue(countNonWhitePixels(
                    new PDFRenderer(document).renderImageWithDPI(0, 96)) > 100);
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
        assertEquals(1, page.fonts().getFirst().rawUnmappedGlyphCount());
        assertTrue(page.spatialEvidence().assessed());
        assertEquals(612, page.spatialEvidence().pageWidthPoints());
        assertEquals(792, page.spatialEvidence().pageHeightPoints());
        assertEquals(1, page.spatialEvidence().totalLocationCount());
        assertFalse(page.spatialEvidence().locationsTruncated());
        FindingLocation location = page.spatialEvidence().locations().getFirst();
        assertEquals(Finding.MISSING_UNICODE, location.code());
        assertTrue(location.widthPoints() > 0);
        assertTrue(location.heightPoints() > 0);
    }

    @Test
    void boundsAndTruncatesFindingLocationsOnRotatedPages() throws IOException {
        Path pdf = temporaryDirectory.resolve("rotated-spatial-evidence.pdf");
        try (PDDocument document = new PDDocument()) {
            PDType3Font font = createUnmappedType3Font(document);
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(90);
            document.addPage(page);
            page.setResources(new PDResources());
            page.getResources().put(COSName.getPDFName("F1"), font);
            PDStream pageContent = new PDStream(document);
            try (var output = pageContent.createOutputStream()) {
                output.write(
                        "BT /F1 12 Tf 72 720 Td <414141414141414141414141> Tj ET"
                                .getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(pageContent);
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();
        SpatialEvidenceAudit spatial = page.spatialEvidence();

        assertEquals(12, page.missingUnicodeGlyphCount());
        assertTrue(spatial.assessed());
        assertEquals(792, spatial.pageWidthPoints());
        assertEquals(612, spatial.pageHeightPoints());
        assertEquals(90, spatial.rotationDegrees());
        assertEquals(SpatialEvidenceAudit.TOP_LEFT_DISPLAY_POINTS, spatial.coordinateSpace());
        assertEquals(12, spatial.totalLocationCount());
        assertEquals(8, spatial.locations().size());
        assertTrue(spatial.locationsTruncated());
        for (FindingLocation location : spatial.locations()) {
            assertEquals(Finding.MISSING_UNICODE, location.code());
            assertTrue(location.xPoints() >= 0);
            assertTrue(location.yPoints() >= 0);
            assertTrue(location.xPoints() + location.widthPoints()
                    <= spatial.pageWidthPoints() + 0.001);
            assertTrue(location.yPoints() + location.heightPoints()
                    <= spatial.pageHeightPoints() + 0.001);
        }
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
    void auditsFormsSignaturesAttachmentsAssociatedFilesAndPortfolios()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("document-surfaces.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            addHelveticaText(document, page, "page text", 72, 720);

            COSDictionary acroForm = new COSDictionary();
            COSArray fields = new COSArray();
            COSDictionary widget = new COSDictionary();
            widget.setItem(COSName.FT, COSName.getPDFName("Tx"));
            widget.setItem(COSName.SUBTYPE, COSName.WIDGET);
            fields.add(widget);
            COSDictionary signature = new COSDictionary();
            signature.setItem(COSName.FT, COSName.getPDFName("Sig"));
            fields.add(signature);
            acroForm.setItem(COSName.FIELDS, fields);
            acroForm.setItem(COSName.getPDFName("XFA"), new COSString("template"));
            document.getDocumentCatalog().getCOSObject().setItem(
                    COSName.getPDFName("AcroForm"), acroForm);

            COSDictionary fileSpecification = new COSDictionary();
            fileSpecification.setItem(COSName.TYPE, COSName.FILESPEC);
            COSArray embeddedNames = new COSArray();
            embeddedNames.add(new COSString("payload.txt"));
            embeddedNames.add(fileSpecification);
            COSDictionary embeddedFiles = new COSDictionary();
            embeddedFiles.setItem(COSName.NAMES, embeddedNames);
            COSDictionary names = new COSDictionary();
            names.setItem(COSName.getPDFName("EmbeddedFiles"), embeddedFiles);
            document.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names);
            document.getDocumentCatalog().getCOSObject().setItem(
                    COSName.getPDFName("AF"), new COSArray(List.of(fileSpecification)));
            document.getDocumentCatalog().getCOSObject().setItem(
                    COSName.getPDFName("Collection"), new COSDictionary());
            document.save(pdf.toFile());
        }

        AuditReport report = new PdfTextLayerAuditor().audit(pdf);
        DocumentSurfaceAudit surfaces = report.documentSurfaces();

        assertTrue(surfaces.assessed());
        assertTrue(surfaces.complete());
        assertEquals(2, surfaces.acroFormFieldCount());
        assertEquals(1, surfaces.signatureFieldCount());
        assertEquals(1, surfaces.widgetWithoutAppearanceCount());
        assertTrue(surfaces.xfaPresent());
        assertEquals(1, surfaces.embeddedFileCount());
        assertEquals(1, surfaces.associatedFileReferenceCount());
        assertTrue(surfaces.portfolioPresent());
        assertTrue(surfaces.requiresProfile());
        assertTrue(report.needsAttention());
    }

    @Test
    void marksCyclicEmbeddedFileNameTreeIncomplete() throws IOException {
        Path pdf = temporaryDirectory.resolve("cyclic-embedded-names.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            addHelveticaText(document, page, "page text", 72, 720);
            COSDictionary embeddedFiles = new COSDictionary();
            embeddedFiles.setItem(COSName.KIDS, new COSArray(List.of(embeddedFiles)));
            COSDictionary names = new COSDictionary();
            names.setItem(COSName.getPDFName("EmbeddedFiles"), embeddedFiles);
            document.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names);
            document.save(pdf.toFile());
        }

        DocumentSurfaceAudit surfaces =
                new PdfTextLayerAuditor().audit(pdf).documentSurfaces();

        assertFalse(surfaces.complete());
        assertTrue(surfaces.requiresProfile());
    }

    @Test
    void enforcesGlyphSemanticCharacterAndFontBudgets() throws IOException {
        Path glyphs = temporaryDirectory.resolve("too-many-glyphs.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            addHelveticaText(document, page, "AB", 72, 720);
            document.save(glyphs.toFile());
        }
        assertWorkLimit(
                glyphs,
                new AuditWorkLimits(1, 100, 10, 10, 10, 10, 10, 10),
                AuditWorkLimitException.Code.GLYPH_COUNT);

        Path characters = temporaryDirectory.resolve("too-many-semantic-characters.pdf");
        try (PDDocument document = new PDDocument()) {
            addActualTextPage(
                    document,
                    createUnmappedType3Font(document),
                    utf16Hex("abc"));
            document.save(characters.toFile());
        }
        assertWorkLimit(
                characters,
                new AuditWorkLimits(10, 2, 10, 10, 10, 10, 10, 10),
                AuditWorkLimitException.Code.SEMANTIC_CHARACTER_COUNT);

        Path fonts = temporaryDirectory.resolve("too-many-fonts.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.showText("A");
                content.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 12);
                content.showText("B");
                content.endText();
            }
            document.save(fonts.toFile());
        }
        assertWorkLimit(
                fonts,
                new AuditWorkLimits(10, 10, 1, 10, 10, 10, 10, 10),
                AuditWorkLimitException.Code.FONT_COUNT);
    }

    @Test
    void enforcesImagePathAnnotationAppearanceAndOptionalContentBudgets()
            throws IOException {
        BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Path images = temporaryDirectory.resolve("too-many-images.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            var image = LosslessFactory.createFromImage(document, pixel);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 0, 0, 10, 10);
                content.drawImage(image, 20, 20, 10, 10);
            }
            document.save(images.toFile());
        }
        assertWorkLimit(
                images,
                new AuditWorkLimits(10, 10, 10, 1, 10, 10, 10, 10),
                AuditWorkLimitException.Code.IMAGE_COUNT);

        Path paths = temporaryDirectory.resolve("too-many-paths.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(0, 0, 10, 10);
                content.fill();
                content.addRect(20, 20, 10, 10);
                content.fill();
            }
            document.save(paths.toFile());
        }
        assertWorkLimit(
                paths,
                new AuditWorkLimits(10, 10, 10, 10, 1, 10, 10, 10),
                AuditWorkLimitException.Code.PAINTED_VECTOR_PATH_COUNT);

        Path annotations = temporaryDirectory.resolve("too-many-annotations.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            page.setAnnotations(List.of(new PDAnnotationWidget(), new PDAnnotationWidget()));
            document.save(annotations.toFile());
        }
        assertWorkLimit(
                annotations,
                new AuditWorkLimits(10, 10, 10, 10, 10, 1, 10, 10),
                AuditWorkLimitException.Code.ANNOTATION_COUNT);

        Path appearances = temporaryDirectory.resolve("too-many-appearances.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDAppearanceDictionary dictionary = new PDAppearanceDictionary();
            dictionary.setNormalAppearance(createAppearance(
                    document,
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    "A"));
            dictionary.setRolloverAppearance(createAppearance(
                    document,
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    "B"));
            PDAnnotationWidget widget = new PDAnnotationWidget();
            widget.setRectangle(new PDRectangle(0, 0, 100, 20));
            widget.setAppearance(dictionary);
            page.setAnnotations(List.of(widget));
            document.save(appearances.toFile());
        }
        assertWorkLimit(
                appearances,
                new AuditWorkLimits(10, 10, 10, 10, 10, 10, 1, 10),
                AuditWorkLimitException.Code.ANNOTATION_APPEARANCE_STREAM_COUNT);

        Path optional = temporaryDirectory.resolve("too-many-optional-refs.pdf");
        try (PDDocument document = new PDDocument()) {
            PDOptionalContentGroup group = new PDOptionalContentGroup("layer");
            PDOptionalContentProperties properties = new PDOptionalContentProperties();
            properties.addGroup(group);
            document.getDocumentCatalog().setOCProperties(properties);
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int index = 0; index < 2; index++) {
                    content.beginMarkedContent(COSName.OC, group);
                    content.endMarkedContent();
                }
            }
            document.save(optional.toFile());
        }
        assertWorkLimit(
                optional,
                new AuditWorkLimits(10, 10, 10, 10, 10, 10, 10, 1),
                AuditWorkLimitException.Code.OPTIONAL_CONTENT_REFERENCE_COUNT);

        Path documentSurfaces = temporaryDirectory.resolve("too-many-document-surfaces.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            COSDictionary acroForm = new COSDictionary();
            COSArray fields = new COSArray();
            fields.add(new COSDictionary());
            fields.add(new COSDictionary());
            acroForm.setItem(COSName.FIELDS, fields);
            document.getDocumentCatalog().getCOSObject().setItem(
                    COSName.getPDFName("AcroForm"), acroForm);
            document.save(documentSurfaces.toFile());
        }
        assertWorkLimit(
                documentSurfaces,
                new AuditWorkLimits(10, 10, 10, 10, 10, 10, 10, 10, 1),
                AuditWorkLimitException.Code.DOCUMENT_SURFACE_COUNT);
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
    void profilesChineseRtlComplexScriptsCombiningEmojiAndFormatCharacters()
            throws IOException {
        Path pdf = temporaryDirectory.resolve("unicode-profile.pdf");
        String semanticText =
                "Latin e\u0301 中文 العربية עברית देवनागरी ไทย 한글 😀 ❤️‍💻 \u200F";
        try (PDDocument document = new PDDocument()) {
            addActualTextPage(
                    document,
                    createUnmappedType3Font(document),
                    utf16Hex(semanticText));
            document.save(pdf.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();
        UnicodeProfileAudit profile = page.unicodeProfile();

        assertTrue(profile.scripts().containsAll(List.of(
                "ARABIC", "DEVANAGARI", "HAN", "HANGUL", "HEBREW", "LATIN", "THAI")));
        assertTrue(profile.rightToLeftCharacterCount() > 0);
        assertTrue(profile.combiningMarkCount() > 0);
        assertTrue(profile.nonBmpCharacterCount() >= 2);
        assertTrue(profile.variationSelectorCount() > 0);
        assertTrue(profile.zeroWidthJoinerCount() > 0);
        assertEquals(1, profile.bidiControlCount());
        assertFalse(page.findings().contains(Finding.MISSING_UNICODE));
    }

    @ParameterizedTest(name = "language text: {0}")
    @MethodSource("exactLanguageMappings")
    void preservesExactSemanticTextAndProfilesItsScripts(
            String label,
            String expectedText,
            List<String> expectedScripts,
            boolean expectedRightToLeft
    ) throws IOException {
        Path pdf = temporaryDirectory.resolve("language-" + label + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDType3Font font = createType3Font(document, "ExactLanguageText");
            font.getCOSObject().setItem(
                    COSName.TO_UNICODE,
                    createToUnicodeCMap(
                            document,
                            "<41> <" + utf16Hex(expectedText) + ">"));
            addType3TextPage(document, font);
            document.save(pdf.toFile());
        }

        String extracted;
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            extracted = new PDFTextStripper().getText(document).strip();
        }
        PageAudit page = new PdfTextLayerAuditor().audit(pdf).pages().getFirst();

        if (expectedRightToLeft) {
            assertFalse(extracted.isBlank());
        } else {
            assertEquals(expectedText, extracted);
        }
        assertEquals(
                expectedText.codePointCount(0, expectedText.length()),
                page.unicodeCharacterCount());
        assertEquals(expectedScripts, page.unicodeProfile().scripts());
        assertEquals(
                expectedRightToLeft,
                page.unicodeProfile().rightToLeftCharacterCount() > 0);
        assertEquals(
                expectedRightToLeft,
                page.findings().contains(Finding.RTL_TEXT_REQUIRES_EXTRACTION_PROFILE));
        assertEquals(0, page.missingUnicodeGlyphCount());
        assertFalse(page.findings().contains(Finding.MISSING_UNICODE));
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

    @Test
    void flagsCompositeFontTextWhoseUnicodeIsOnlyImplicitlyInferred()
            throws IOException {
        Path authored = temporaryDirectory.resolve("authored-type0.pdf");
        Path implicit = temporaryDirectory.resolve("implicit-type0.pdf");
        String text = "ABC";
        try (PDDocument document = new PDDocument();
                InputStream input = PDTrueTypeFont.class.getResourceAsStream(
                        "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            if (input == null) {
                throw new IllegalStateException("PDFBox Unicode test font is unavailable.");
            }
            PDPage page = new PDPage();
            document.addPage(page);
            PDType0Font font = PDType0Font.load(document, input, false);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 16);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(authored.toFile());
        }
        try (PDDocument document = Loader.loadPDF(authored.toFile())) {
            COSName fontName = document.getPage(0).getResources().getFontNames()
                    .iterator().next();
            PDFont font = document.getPage(0).getResources().getFont(fontName);
            font.getCOSObject().removeItem(COSName.TO_UNICODE);
            document.save(implicit.toFile());
        }

        PageAudit page = new PdfTextLayerAuditor().audit(implicit).pages().getFirst();

        assertEquals(text.length(), page.glyphCount());
        assertEquals(
                text.length(),
                page.semanticMapping().implicitCompositeMappingGlyphCount());
        assertTrue(page.findings().contains(Finding.IMPLICIT_COMPOSITE_UNICODE_MAPPING));
        assertFalse(page.findings().contains(Finding.MISSING_UNICODE));
        assertFalse(page.fonts().getFirst().toUnicodePresent());
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

    private static Stream<Arguments> exactLanguageMappings() {
        return Stream.of(
                Arguments.of("latin", "Zażółć gęślą jaźń", List.of("LATIN"), false),
                Arguments.of("vietnamese-nfd", "Tiếng Việt", List.of("LATIN"), false),
                Arguments.of("cyrillic", "Привет світ", List.of("CYRILLIC"), false),
                Arguments.of("greek", "Ελληνικά", List.of("GREEK"), false),
                Arguments.of("arabic", "العربية ١٢٣", List.of("ARABIC"), true),
                Arguments.of("hebrew", "עברית 123", List.of("HEBREW"), true),
                Arguments.of("syriac", "ܣܘܪܝܝܐ", List.of("SYRIAC"), true),
                Arguments.of("devanagari", "हिन्दी", List.of("DEVANAGARI"), false),
                Arguments.of("bengali", "বাংলা", List.of("BENGALI"), false),
                Arguments.of("tamil", "தமிழ்", List.of("TAMIL"), false),
                Arguments.of("thai", "ภาษาไทย", List.of("THAI"), false),
                Arguments.of("chinese", "中文繁體", List.of("HAN"), false),
                Arguments.of(
                        "japanese",
                        "日本語かなカナ",
                        List.of("HAN", "HIRAGANA", "KATAKANA"),
                        false),
                Arguments.of("korean", "한국어", List.of("HANGUL"), false),
                Arguments.of("armenian", "Հայերեն", List.of("ARMENIAN"), false),
                Arguments.of("georgian", "ქართული", List.of("GEORGIAN"), false),
                Arguments.of("ethiopic", "አማርኛ", List.of("ETHIOPIC"), false)
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

    private static void assertWorkLimit(
            Path pdf,
            AuditWorkLimits limits,
            AuditWorkLimitException.Code expectedCode
    ) {
        AuditWorkLimitException exception = assertThrows(
                AuditWorkLimitException.class,
                () -> new PdfTextLayerAuditor(
                        100 * 1024 * 1024L,
                        100,
                        PdfTextLayerAuditor.DEFAULT_TINY_TEXT_THRESHOLD_POINTS,
                        limits).audit(pdf));
        assertEquals(expectedCode, exception.code());
        assertTrue(exception.getMessage().contains("configured"));
    }

    private static String utf16Hex(String text) {
        StringBuilder hex = new StringBuilder(text.length() * 4);
        for (byte value : text.getBytes(StandardCharsets.UTF_16BE)) {
            hex.append(String.format(java.util.Locale.ROOT, "%02X", value & 0xFF));
        }
        return hex.toString();
    }

    private static PDAppearanceStream createAppearance(
            PDDocument document,
            org.apache.pdfbox.pdmodel.font.PDFont font,
            String text
    ) throws IOException {
        PDAppearanceStream appearance = new PDAppearanceStream(document);
        appearance.setBBox(new PDRectangle(120, 30));
        appearance.setResources(new PDResources());
        try (PDPageContentStream content =
                new PDPageContentStream(document, appearance)) {
            content.beginText();
            content.setFont(font, 12);
            content.newLineAtOffset(5, 10);
            content.showText(text);
            content.endText();
        }
        return appearance;
    }

    private static PDOptionalContentMembershipDictionary membership(
            List<PDOptionalContentGroup> groups,
            COSName policy,
            COSArray expression
    ) {
        PDOptionalContentMembershipDictionary membership =
                new PDOptionalContentMembershipDictionary();
        membership.setOCGs(new java.util.ArrayList<>(groups));
        if (policy != null) {
            membership.setVisibilityPolicy(policy);
        }
        if (expression != null) {
            membership.getCOSObject().setItem(COSName.VE, expression);
        }
        return membership;
    }

    private static COSArray expression(
            String operator,
            PDOptionalContentGroup... groups
    ) {
        COSArray expression = new COSArray();
        expression.add(COSName.getPDFName(operator));
        for (PDOptionalContentGroup group : groups) {
            expression.add(group.getCOSObject());
        }
        return expression;
    }

    @SuppressWarnings("deprecation")
    private static PDAppearanceStream createUnmappedAppearance(PDDocument document)
            throws IOException {
        PDAppearanceStream appearance = new PDAppearanceStream(document);
        appearance.setBBox(new PDRectangle(120, 30));
        PDResources resources = new PDResources();
        COSName fontName = resources.add(createUnmappedType3Font(document));
        appearance.setResources(resources);
        try (PDPageContentStream content =
                new PDPageContentStream(document, appearance)) {
            content.appendRawCommands(
                    "BT /" + fontName.getName() + " 12 Tf 5 10 Td <41> Tj ET\n");
        }
        return appearance;
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
