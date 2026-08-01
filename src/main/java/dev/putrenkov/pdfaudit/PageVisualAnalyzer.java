package dev.putrenkov.pdfaudit;

import java.awt.geom.Area;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

final class PageVisualAnalyzer extends PDFGraphicsStreamEngine {
    private static final COSName OPTIONAL_CONTENT = COSName.getPDFName("OC");
    private static final COSName OPTIONAL_CONTENT_GROUP = COSName.getPDFName("OCG");
    private static final COSName OPTIONAL_CONTENT_MEMBERSHIP = COSName.getPDFName("OCMD");

    private final GeneralPath currentPath = new GeneralPath();
    private int imageCount;
    private double maxImageCoverageRatio;
    private final Area combinedImageArea = new Area();
    private int paintedVectorPathCount;
    private boolean optionalContentPresent;
    private final Set<GlyphLocation> glyphLocations = new HashSet<>();
    private int visibleGlyphCount;
    private int invisibleGlyphCount;
    private int offPageGlyphCount;
    private int clippedGlyphCount;
    private int transparentGlyphCount;
    private int duplicateOverlapGlyphCount;
    private int rotatedGlyphCount;
    private int verticalGlyphCount;

    private PageVisualAnalyzer(PDPage page) {
        super(page);
    }

    static Map<Integer, PageEvidence> analyze(
            PDDocument document,
            List<Integer> selectedPages
    ) throws IOException {
        Map<Integer, PageEvidence> results = new LinkedHashMap<>();
        for (int pageNumber : selectedPages) {
            PDPage page = document.getPage(pageNumber - 1);
            PageVisualAnalyzer analyzer = new PageVisualAnalyzer(page);
            analyzer.processPage(page);
            List<PDAnnotation> annotations = page.getAnnotations();
            int widgetCount = (int) annotations.stream()
                    .filter(PDAnnotationWidget.class::isInstance)
                    .count();
            boolean annotationOptionalContent = annotations.stream()
                    .anyMatch(annotation -> annotation.getOptionalContent() != null);
            results.put(pageNumber, new PageEvidence(
                    new VisualContentAudit(
                            true,
                            analyzer.imageCount,
                            analyzer.maxImageCoverageRatio,
                            analyzer.combinedImageCoverageRatio(),
                            analyzer.paintedVectorPathCount,
                            annotations.size(),
                            widgetCount,
                            analyzer.optionalContentPresent || annotationOptionalContent),
                    new GeometryVisibilityAudit(
                            true,
                            analyzer.visibleGlyphCount,
                            analyzer.invisibleGlyphCount,
                            analyzer.offPageGlyphCount,
                            analyzer.clippedGlyphCount,
                            analyzer.transparentGlyphCount,
                            analyzer.duplicateOverlapGlyphCount,
                            analyzer.rotatedGlyphCount,
                            analyzer.verticalGlyphCount)));
        }
        return results;
    }

    @Override
    protected void showGlyph(
            Matrix textRenderingMatrix,
            PDFont font,
            int code,
            Vector displacement
    ) throws IOException {
        RenderingMode renderingMode = getGraphicsState().getTextState().getRenderingMode();
        boolean invisible = !renderingMode.isFill() && !renderingMode.isStroke();
        boolean transparent = !invisible
                && (!renderingMode.isFill()
                        || getGraphicsState().getNonStrokeAlphaConstant() <= 0)
                && (!renderingMode.isStroke()
                        || getGraphicsState().getAlphaConstant() <= 0);
        double x = textRenderingMatrix.getTranslateX();
        double y = textRenderingMatrix.getTranslateY();
        PDRectangle crop = getPage().getCropBox();
        boolean offPage = x < crop.getLowerLeftX()
                || y < crop.getLowerLeftY()
                || x > crop.getUpperRightX()
                || y > crop.getUpperRightY();
        Area clippingPath = getGraphicsState().getCurrentClippingPath();
        boolean clipped = !offPage && clippingPath != null && !clippingPath.contains(x, y);

        if (invisible) {
            invisibleGlyphCount++;
        }
        if (transparent) {
            transparentGlyphCount++;
        }
        if (offPage) {
            offPageGlyphCount++;
        }
        if (clipped) {
            clippedGlyphCount++;
        }
        if (!invisible && !transparent && !offPage && !clipped) {
            visibleGlyphCount++;
        }
        double angle = Math.atan2(
                textRenderingMatrix.getShearY(),
                textRenderingMatrix.getScaleX());
        if (Math.abs(angle) > 0.0001) {
            rotatedGlyphCount++;
        }
        if (font != null && font.isVertical()) {
            verticalGlyphCount++;
        }
        GlyphLocation location = new GlyphLocation(
                Math.round(x * 1_000),
                Math.round(y * 1_000),
                code,
                font == null ? "<unknown>" : String.valueOf(font.getName()));
        if (!glyphLocations.add(location)) {
            duplicateOverlapGlyphCount++;
        }
        super.showGlyph(textRenderingMatrix, font, code, displacement);
    }

    @Override
    protected void operatorException(
            Operator operator,
            List<COSBase> operands,
            IOException exception
    ) throws IOException {
        if (OperatorName.SET_FONT_AND_SIZE.equals(operator.getName())
                && isRecoverableMalformedType0Font(exception)) {
            getGraphicsState().getTextState().setFont(null);
            return;
        }
        super.operatorException(operator, operands, exception);
    }

    @Override
    public void beginMarkedContentSequence(COSName tag, COSDictionary properties) {
        if (OPTIONAL_CONTENT.equals(tag) || isOptionalContent(properties)) {
            optionalContentPresent = true;
        }
        super.beginMarkedContentSequence(tag, properties);
    }

    @Override
    public void drawImage(PDImage image) {
        imageCount++;
        if (image instanceof PDImageXObject imageXObject
                && imageXObject.getOptionalContent() != null) {
            optionalContentPresent = true;
        }

        Area paintedArea = new Area(getGraphicsState()
                .getCurrentTransformationMatrix()
                .createAffineTransform()
                .createTransformedShape(new Rectangle2D.Double(0, 0, 1, 1)));
        PDRectangle cropBox = getPage().getCropBox();
        Area pageArea = new Area(new Rectangle2D.Double(
                cropBox.getLowerLeftX(),
                cropBox.getLowerLeftY(),
                cropBox.getWidth(),
                cropBox.getHeight()));
        paintedArea.intersect(pageArea);
        Area clippingPath = getGraphicsState().getCurrentClippingPath();
        if (clippingPath != null) {
            paintedArea.intersect(clippingPath);
        }

        double pageAreaValue = cropBox.getWidth() * cropBox.getHeight();
        double coverage = pageAreaValue <= 0 ? 0 : area(paintedArea) / pageAreaValue;
        maxImageCoverageRatio = Math.max(
                maxImageCoverageRatio,
                Math.max(0, Math.min(1, coverage)));
        combinedImageArea.add(paintedArea);
    }

    @Override
    public void appendRectangle(
            Point2D point0,
            Point2D point1,
            Point2D point2,
            Point2D point3
    ) {
        currentPath.moveTo(point0.getX(), point0.getY());
        currentPath.lineTo(point1.getX(), point1.getY());
        currentPath.lineTo(point2.getX(), point2.getY());
        currentPath.lineTo(point3.getX(), point3.getY());
        currentPath.closePath();
    }

    @Override
    public void clip(int windingRule) {
        currentPath.setWindingRule(windingRule);
        if (!currentPath.getPathIterator(null).isDone()) {
            getGraphicsState().intersectClippingPath(currentPath);
        }
    }

    @Override
    public void moveTo(float x, float y) {
        currentPath.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        currentPath.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        currentPath.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
        return currentPath.getCurrentPoint();
    }

    @Override
    public void closePath() {
        currentPath.closePath();
    }

    @Override
    public void endPath() {
        currentPath.reset();
    }

    @Override
    public void strokePath() {
        recordPaintedPath();
    }

    @Override
    public void fillPath(int windingRule) {
        currentPath.setWindingRule(windingRule);
        recordPaintedPath();
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        currentPath.setWindingRule(windingRule);
        recordPaintedPath();
    }

    @Override
    public void shadingFill(COSName shadingName) {
        paintedVectorPathCount++;
    }

    private void recordPaintedPath() {
        if (!currentPath.getPathIterator(null).isDone()) {
            paintedVectorPathCount++;
        }
        currentPath.reset();
    }

    private static boolean isOptionalContent(COSDictionary properties) {
        if (properties == null) {
            return false;
        }
        COSName type = properties.getCOSName(COSName.TYPE);
        return OPTIONAL_CONTENT_GROUP.equals(type)
                || OPTIONAL_CONTENT_MEMBERSHIP.equals(type);
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

    private static double area(Area area) {
        FlatteningPathIterator iterator = new FlatteningPathIterator(
                area.getPathIterator(null), 0.25);
        double[] coordinates = new double[6];
        double total = 0;
        double startX = 0;
        double startY = 0;
        double previousX = 0;
        double previousY = 0;
        double ring = 0;
        while (!iterator.isDone()) {
            int segment = iterator.currentSegment(coordinates);
            switch (segment) {
                case PathIterator.SEG_MOVETO -> {
                    startX = coordinates[0];
                    startY = coordinates[1];
                    previousX = startX;
                    previousY = startY;
                    ring = 0;
                }
                case PathIterator.SEG_LINETO -> {
                    ring += previousX * coordinates[1] - coordinates[0] * previousY;
                    previousX = coordinates[0];
                    previousY = coordinates[1];
                }
                case PathIterator.SEG_CLOSE -> {
                    ring += previousX * startY - startX * previousY;
                    total += ring / 2;
                }
                default -> throw new IllegalStateException("Unexpected flattened path segment");
            }
            iterator.next();
        }
        return Math.abs(total);
    }

    private double combinedImageCoverageRatio() {
        PDRectangle cropBox = getPage().getCropBox();
        double pageAreaValue = cropBox.getWidth() * cropBox.getHeight();
        if (pageAreaValue <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, area(combinedImageArea) / pageAreaValue));
    }

    record PageEvidence(
            VisualContentAudit visualContent,
            GeometryVisibilityAudit geometryVisibility
    ) {
    }

    private record GlyphLocation(long x, long y, int code, String fontName) {
    }
}
