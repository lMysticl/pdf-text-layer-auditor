package dev.putrenkov.pdfaudit;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentMembershipDictionary;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.pdmodel.graphics.PDLineDashPattern;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.rendering.RenderDestination;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

final class PageVisualAnalyzer extends PDFGraphicsStreamEngine {
    private static final int SPATIAL_GRID_SIZE = 8;
    private static final int TILED_IMAGE_UNION_THRESHOLD = 64;
    private static final int MAX_VISUAL_REGION_SAMPLES = 32;
    private static final COSName OPTIONAL_CONTENT = COSName.getPDFName("OC");
    private static final COSName OPTIONAL_CONTENT_GROUP = COSName.getPDFName("OCG");
    private static final COSName OPTIONAL_CONTENT_MEMBERSHIP = COSName.getPDFName("OCMD");

    private final GeneralPath currentPath = new GeneralPath();
    private final PDOptionalContentProperties optionalContentProperties;
    private final AuditWorkLimits workLimits;
    private int imageCount;
    private double maxImageCoverageRatio;
    private final AdaptiveAreaUnion combinedImageArea;
    private final Set<Integer> imageGridCells = new HashSet<>();
    private final Map<VisualRegionType, List<VisualRegion>> visualRegions =
            new EnumMap<>(VisualRegionType.class);
    private final Map<VisualRegionType, Long> visualRegionCounts =
            new EnumMap<>(VisualRegionType.class);
    private final Set<Integer> visibleTextGridCells = new HashSet<>();
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
    private boolean inAnnotationAppearance;
    private int annotationAppearanceStreamCount;
    private int annotationGlyphCount;
    private int annotationUnicodeCharacterCount;
    private int annotationMissingUnicodeGlyphCount;
    private int annotationReplacementCharacterCount;
    private int optionalContentReferenceCount;
    private int optionalContentMembershipReferenceCount;
    private int hiddenInViewReferenceCount;
    private int hiddenInPrintReferenceCount;
    private int hiddenInExportReferenceCount;
    private int optionalContentEvaluationFailureCount;
    private int hiddenVisualContentDepth;

    private PageVisualAnalyzer(
            PDPage page,
            PDOptionalContentProperties optionalContentProperties,
            AuditWorkLimits workLimits
    ) {
        super(page);
        this.optionalContentProperties = optionalContentProperties;
        this.workLimits = workLimits;
        this.combinedImageArea = new AdaptiveAreaUnion(page.getCropBox());
        for (VisualRegionType type : VisualRegionType.values()) {
            visualRegions.put(type, new ArrayList<>());
            visualRegionCounts.put(type, 0L);
        }
    }

    static Map<Integer, PageEvidence> analyze(
            PDDocument document,
            List<Integer> selectedPages,
            AuditWorkLimits workLimits
    ) throws IOException {
        Map<Integer, PageEvidence> results = new LinkedHashMap<>();
        for (int pageNumber : selectedPages) {
            PDPage page = document.getPage(pageNumber - 1);
            PageVisualAnalyzer analyzer = new PageVisualAnalyzer(
                    page,
                    document.getDocumentCatalog().getOCProperties(),
                    workLimits);
            analyzer.processPage(page);
            List<PDAnnotation> annotations = page.getAnnotations();
            if (annotations.size() > workLimits.maximumAnnotationCount()) {
                throw new AuditWorkLimitException(
                        AuditWorkLimitException.Code.ANNOTATION_COUNT,
                        workLimits.maximumAnnotationCount());
            }
            for (PDAnnotation annotation : annotations) {
                analyzer.recordOptionalContent(annotation.getOptionalContent());
                analyzer.recordAnnotationRegion(annotation);
                analyzer.processAnnotationAppearances(annotation);
            }
            int widgetCount = (int) annotations.stream()
                    .filter(PDAnnotationWidget.class::isInstance)
                    .count();
            boolean annotationOptionalContent = annotations.stream()
                    .anyMatch(annotation -> annotation.getOptionalContent() != null);
            GridCoverage gridCoverage = analyzer.gridCoverage();
            results.put(pageNumber, new PageEvidence(
                    new VisualContentAudit(
                            true,
                            analyzer.imageCount,
                            analyzer.maxImageCoverageRatio,
                            analyzer.combinedImageCoverageRatio(),
                            gridCoverage.imageCellCount(),
                            gridCoverage.imageTextOverlapCellCount(),
                            gridCoverage.imageTextOverlapRatio(),
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
                            analyzer.verticalGlyphCount),
                    new AnnotationAppearanceAudit(
                            true,
                            analyzer.annotationAppearanceStreamCount,
                            analyzer.annotationGlyphCount,
                            analyzer.annotationUnicodeCharacterCount,
                            analyzer.annotationMissingUnicodeGlyphCount,
                            analyzer.annotationReplacementCharacterCount),
                    new OptionalContentAudit(
                            analyzer.optionalContentEvaluationFailureCount == 0,
                            analyzer.optionalContentReferenceCount,
                            analyzer.optionalContentMembershipReferenceCount,
                            analyzer.hiddenInViewReferenceCount,
                            analyzer.hiddenInPrintReferenceCount,
                            analyzer.hiddenInExportReferenceCount,
                            analyzer.optionalContentEvaluationFailureCount),
                    analyzer.visualRegionAudit()));
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
            if (!inAnnotationAppearance) {
                recordVisibleTextGridCell(x, y);
            }
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
        if (inAnnotationAppearance) {
            annotationGlyphCount++;
            String unicode = annotationUnicode(font, code);
            if (!PdfTextLayerAuditor.isUsableUnicode(unicode)) {
                annotationMissingUnicodeGlyphCount++;
            }
            if (unicode != null) {
                annotationUnicodeCharacterCount +=
                        unicode.codePointCount(0, unicode.length());
                annotationReplacementCharacterCount += (int) unicode.codePoints()
                        .filter(codePoint -> codePoint == 0xFFFD)
                        .count();
            }
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
        if (hiddenVisualContentDepth > 0) {
            hiddenVisualContentDepth++;
        } else if (OPTIONAL_CONTENT.equals(tag) || isOptionalContent(properties)) {
            optionalContentPresent = true;
            PDPropertyList optionalContent = properties == null
                    ? null
                    : PDPropertyList.create(properties);
            recordOptionalContent(optionalContent);
            if (optionalContent == null || !isVisibleInView(optionalContent)) {
                hiddenVisualContentDepth = 1;
            }
        }
        super.beginMarkedContentSequence(tag, properties);
    }

    @Override
    public void endMarkedContentSequence() {
        try {
            super.endMarkedContentSequence();
        } finally {
            if (hiddenVisualContentDepth > 0) {
                hiddenVisualContentDepth--;
            }
        }
    }

    @Override
    public void drawImage(PDImage image) {
        imageCount++;
        if (imageCount > workLimits.maximumImageCount()) {
            throw new AuditWorkLimitException(
                    AuditWorkLimitException.Code.IMAGE_COUNT,
                    workLimits.maximumImageCount());
        }
        boolean regionVisible = hiddenVisualContentDepth == 0
                && getGraphicsState().getNonStrokeAlphaConstant() > 0;
        if (image instanceof PDImageXObject imageXObject
                && imageXObject.getOptionalContent() != null) {
            optionalContentPresent = true;
            PDPropertyList optionalContent = imageXObject.getOptionalContent();
            recordOptionalContent(optionalContent);
            regionVisible &= isVisibleInView(optionalContent);
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
        recordImageGridCells(paintedArea);
        if (regionVisible) {
            recordVisualRegion(VisualRegionType.IMAGE, paintedArea);
        }
    }

    @Override
    public void showForm(PDFormXObject form) throws IOException {
        boolean hidden = false;
        if (form.getOptionalContent() != null) {
            optionalContentPresent = true;
            recordOptionalContent(form.getOptionalContent());
            hidden = !isVisibleInView(form.getOptionalContent());
        }
        if (hidden) {
            hiddenVisualContentDepth++;
        }
        try {
            super.showForm(form);
        } finally {
            if (hidden) {
                hiddenVisualContentDepth--;
            }
        }
    }

    @Override
    public void showTransparencyGroup(PDTransparencyGroup form) throws IOException {
        boolean hidden = false;
        if (form.getOptionalContent() != null) {
            optionalContentPresent = true;
            recordOptionalContent(form.getOptionalContent());
            hidden = !isVisibleInView(form.getOptionalContent());
        }
        if (hidden) {
            hiddenVisualContentDepth++;
        }
        try {
            super.showTransparencyGroup(form);
        } finally {
            if (hidden) {
                hiddenVisualContentDepth--;
            }
        }
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
        recordPaintedPath(false, true);
    }

    @Override
    public void fillPath(int windingRule) {
        currentPath.setWindingRule(windingRule);
        recordPaintedPath(true, false);
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        currentPath.setWindingRule(windingRule);
        recordPaintedPath(true, true);
    }

    @Override
    public void shadingFill(COSName shadingName) {
        recordPaintedVectorOperation();
    }

    private void recordPaintedPath(boolean fill, boolean stroke) {
        if (!currentPath.getPathIterator(null).isDone()) {
            recordPaintedVectorOperation();
            recordPaintedPathRegion(fill, stroke);
        }
        currentPath.reset();
    }

    private void recordPaintedPathRegion(boolean fill, boolean stroke) {
        if (hiddenVisualContentDepth > 0) {
            return;
        }
        Area paintedArea = new Area();
        if (fill && getGraphicsState().getNonStrokeAlphaConstant() > 0) {
            paintedArea.add(new Area(currentPath));
        }
        if (stroke && getGraphicsState().getAlphaConstant() > 0) {
            BasicStroke currentStroke = currentStroke();
            if (currentStroke != null) {
                paintedArea.add(new Area(currentStroke.createStrokedShape(currentPath)));
            }
        }
        if (paintedArea.isEmpty()) {
            return;
        }
        Area clippingPath = getGraphicsState().getCurrentClippingPath();
        if (clippingPath != null) {
            paintedArea.intersect(new Area(clippingPath));
        }
        PDRectangle crop = getPage().getCropBox();
        paintedArea.intersect(new Area(new Rectangle2D.Double(
                crop.getLowerLeftX(),
                crop.getLowerLeftY(),
                crop.getWidth(),
                crop.getHeight())));
        recordVisualRegion(VisualRegionType.VECTOR_PATH, paintedArea);
    }

    /**
     * Mirrors PDFBox's page-renderer stroke geometry in page coordinates. Non-finite dash arrays
     * use the solid path envelope, while an all-zero dash is not visible at all.
     */
    private BasicStroke currentStroke() {
        float lineWidth = transformWidth(getGraphicsState().getLineWidth());
        if (!Float.isFinite(lineWidth)) {
            return null;
        }
        lineWidth = Math.max(lineWidth, 0.25f);
        int lineCap = Math.min(2, Math.max(0, getGraphicsState().getLineCap()));
        int lineJoin = Math.min(2, Math.max(0, getGraphicsState().getLineJoin()));
        float miterLimit = getGraphicsState().getMiterLimit();
        if (!Float.isFinite(miterLimit) || miterLimit < 1) {
            miterLimit = 10;
        }

        PDLineDashPattern dashPattern = getGraphicsState().getLineDashPattern();
        float[] dashArray = dashPattern.getDashArray();
        if (dashArray.length > 0 && allZero(dashArray)) {
            return null;
        }
        float dashPhase = 0;
        if (dashArray.length == 0 || !transformDashArray(dashArray)) {
            dashArray = null;
        } else {
            dashPhase = transformWidth(dashPattern.getPhase());
            if (!Float.isFinite(dashPhase) || dashPhase < 0) {
                dashArray = null;
                dashPhase = 0;
            } else {
                dashPhase = Math.min(dashPhase, Short.MAX_VALUE);
            }
        }
        return new BasicStroke(
                lineWidth,
                lineCap,
                lineJoin,
                miterLimit,
                dashArray,
                dashPhase);
    }

    private boolean transformDashArray(float[] dashArray) {
        for (int index = 0; index < dashArray.length; index++) {
            if (!Float.isFinite(dashArray[index])) {
                return false;
            }
            dashArray[index] = transformWidth(dashArray[index]);
            if (!Float.isFinite(dashArray[index])) {
                return false;
            }
            dashArray[index] = Math.max(dashArray[index], 0.062f);
        }
        return true;
    }

    private static boolean allZero(float[] values) {
        for (float value : values) {
            if (value != 0) {
                return false;
            }
        }
        return true;
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

    private void processAnnotationAppearances(PDAnnotation annotation) throws IOException {
        Map<COSStream, PDAppearanceStream> streams = new IdentityHashMap<>();
        PDAppearanceDictionary appearance = annotation.getAppearance();
        if (appearance != null) {
            addAppearanceEntry(streams, appearance.getNormalAppearance());
            addAppearanceEntry(streams, appearance.getRolloverAppearance());
            addAppearanceEntry(streams, appearance.getDownAppearance());
        }
        for (PDAppearanceStream stream : streams.values()) {
            annotationAppearanceStreamCount++;
            if (annotationAppearanceStreamCount
                    > workLimits.maximumAnnotationAppearanceStreamCount()) {
                throw new AuditWorkLimitException(
                        AuditWorkLimitException.Code.ANNOTATION_APPEARANCE_STREAM_COUNT,
                        workLimits.maximumAnnotationAppearanceStreamCount());
            }
            inAnnotationAppearance = true;
            try {
                processAnnotation(annotation, stream);
            } finally {
                inAnnotationAppearance = false;
            }
        }
    }

    private static void addAppearanceEntry(
            Map<COSStream, PDAppearanceStream> streams,
            PDAppearanceEntry entry
    ) {
        if (entry == null) {
            return;
        }
        if (entry.isStream()) {
            PDAppearanceStream stream = entry.getAppearanceStream();
            streams.put(stream.getCOSObject(), stream);
        } else if (entry.isSubDictionary()) {
            for (PDAppearanceStream stream : entry.getSubDictionary().values()) {
                streams.put(stream.getCOSObject(), stream);
            }
        }
    }

    private static String annotationUnicode(PDFont font, int code) {
        if (font == null) {
            return null;
        }
        try {
            return font.toUnicode(code);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void recordOptionalContent(PDPropertyList propertyList) {
        if (propertyList == null) {
            optionalContentEvaluationFailureCount += 3;
            return;
        }
        optionalContentReferenceCount++;
        if (optionalContentReferenceCount
                > workLimits.maximumOptionalContentReferenceCount()) {
            throw new AuditWorkLimitException(
                    AuditWorkLimitException.Code.OPTIONAL_CONTENT_REFERENCE_COUNT,
                    workLimits.maximumOptionalContentReferenceCount());
        }
        if (propertyList instanceof PDOptionalContentMembershipDictionary) {
            optionalContentMembershipReferenceCount++;
        }
        Boolean hiddenView = isHidden(propertyList, RenderDestination.VIEW, 0,
                identitySet());
        Boolean hiddenPrint = isHidden(propertyList, RenderDestination.PRINT, 0,
                identitySet());
        Boolean hiddenExport = isHidden(propertyList, RenderDestination.EXPORT, 0,
                identitySet());
        hiddenInViewReferenceCount += recordVisibility(hiddenView);
        hiddenInPrintReferenceCount += recordVisibility(hiddenPrint);
        hiddenInExportReferenceCount += recordVisibility(hiddenExport);
    }

    private int recordVisibility(Boolean hidden) {
        if (hidden == null) {
            optionalContentEvaluationFailureCount++;
            return 0;
        }
        return hidden ? 1 : 0;
    }

    private boolean isVisibleInView(PDPropertyList propertyList) {
        return Boolean.FALSE.equals(isHidden(
                propertyList,
                RenderDestination.VIEW,
                0,
                identitySet()));
    }

    private Boolean isHidden(
            PDPropertyList propertyList,
            RenderDestination destination,
            int depth,
            Set<COSBase> visiting
    ) {
        if (depth > 64 || !visiting.add(propertyList.getCOSObject())) {
            return null;
        }
        try {
            if (propertyList instanceof PDOptionalContentGroup group) {
                PDOptionalContentGroup.RenderState state = group.getRenderState(destination);
                if (state != null) {
                    return state == PDOptionalContentGroup.RenderState.OFF;
                }
                return optionalContentProperties != null
                        && !optionalContentProperties.isGroupEnabled(group);
            }
            if (propertyList instanceof PDOptionalContentMembershipDictionary membership) {
                return isMembershipHidden(membership, destination, depth + 1, visiting);
            }
            return false;
        } catch (RuntimeException exception) {
            return null;
        } finally {
            visiting.remove(propertyList.getCOSObject());
        }
    }

    private Boolean isMembershipHidden(
            PDOptionalContentMembershipDictionary membership,
            RenderDestination destination,
            int depth,
            Set<COSBase> visiting
    ) {
        COSArray expression = membership.getCOSObject().getCOSArray(COSName.VE);
        if (expression != null && expression.size() > 0) {
            return isExpressionHidden(expression, destination, depth, visiting);
        }
        List<PDPropertyList> groups = membership.getOCGs();
        if (groups.isEmpty()) {
            return false;
        }
        List<Boolean> visible = new ArrayList<>(groups.size());
        for (PDPropertyList group : groups) {
            Boolean hidden = isHidden(group, destination, depth, visiting);
            if (hidden == null) {
                return null;
            }
            visible.add(!hidden);
        }
        COSName policy = membership.getVisibilityPolicy();
        if (COSName.ANY_OFF.equals(policy)) {
            return visible.stream().allMatch(Boolean::booleanValue);
        }
        if (COSName.ALL_ON.equals(policy)) {
            return visible.stream().anyMatch(value -> !value);
        }
        if (COSName.ALL_OFF.equals(policy)) {
            return visible.stream().anyMatch(Boolean::booleanValue);
        }
        return visible.stream().noneMatch(Boolean::booleanValue);
    }

    private Boolean isExpressionHidden(
            COSArray expression,
            RenderDestination destination,
            int depth,
            Set<COSBase> visiting
    ) {
        if (depth > 64 || !visiting.add(expression)) {
            return null;
        }
        try {
            if (expression.size() == 0) {
                return false;
            }
            String operator = expression.getName(0);
            if (operator == null || expression.size() < 2) {
                return null;
            }
            List<Boolean> hidden = new ArrayList<>();
            for (int index = 1; index < expression.size(); index++) {
                COSBase operand = expression.getObject(index);
                Boolean value;
                if (operand instanceof COSArray nested) {
                    value = isExpressionHidden(nested, destination, depth + 1, visiting);
                } else if (operand instanceof COSDictionary dictionary) {
                    value = isHidden(
                            PDPropertyList.create(dictionary),
                            destination,
                            depth + 1,
                            visiting);
                } else {
                    return null;
                }
                if (value == null) {
                    return null;
                }
                hidden.add(value);
            }
            return switch (operator) {
                case "And" -> hidden.stream().anyMatch(Boolean::booleanValue);
                case "Or" -> hidden.stream().allMatch(Boolean::booleanValue);
                case "Not" -> hidden.size() == 1 ? !hidden.getFirst() : null;
                default -> null;
            };
        } finally {
            visiting.remove(expression);
        }
    }

    private static Set<COSBase> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private void recordPaintedVectorOperation() {
        paintedVectorPathCount++;
        if (paintedVectorPathCount > workLimits.maximumPaintedVectorPathCount()) {
            throw new AuditWorkLimitException(
                    AuditWorkLimitException.Code.PAINTED_VECTOR_PATH_COUNT,
                    workLimits.maximumPaintedVectorPathCount());
        }
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
        return Math.max(0, Math.min(1, combinedImageArea.area() / pageAreaValue));
    }

    private void recordImageGridCells(Area paintedArea) {
        PDRectangle crop = getPage().getCropBox();
        if (crop.getWidth() <= 0 || crop.getHeight() <= 0) {
            return;
        }
        double cellWidth = crop.getWidth() / SPATIAL_GRID_SIZE;
        double cellHeight = crop.getHeight() / SPATIAL_GRID_SIZE;
        for (int row = 0; row < SPATIAL_GRID_SIZE; row++) {
            for (int column = 0; column < SPATIAL_GRID_SIZE; column++) {
                Rectangle2D cell = new Rectangle2D.Double(
                        crop.getLowerLeftX() + column * cellWidth,
                        crop.getLowerLeftY() + row * cellHeight,
                        cellWidth,
                        cellHeight);
                if (paintedArea.intersects(cell)) {
                    imageGridCells.add(row * SPATIAL_GRID_SIZE + column);
                }
            }
        }
    }

    private void recordAnnotationRegion(PDAnnotation annotation) {
        if (annotation.isHidden() || annotation.isInvisible() || annotation.isNoView()) {
            return;
        }
        PDPropertyList optionalContent = annotation.getOptionalContent();
        if (optionalContent != null) {
            Boolean hiddenInView = isHidden(
                    optionalContent,
                    RenderDestination.VIEW,
                    0,
                    identitySet());
            if (!Boolean.FALSE.equals(hiddenInView)) {
                return;
            }
        }
        PDRectangle rectangle = annotation.getRectangle();
        if (rectangle == null) {
            return;
        }
        double x = Math.min(rectangle.getLowerLeftX(), rectangle.getUpperRightX());
        double y = Math.min(rectangle.getLowerLeftY(), rectangle.getUpperRightY());
        double width = Math.abs(rectangle.getWidth());
        double height = Math.abs(rectangle.getHeight());
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(width)
                || !Double.isFinite(height)
                || width <= 0
                || height <= 0) {
            return;
        }
        Area visibleArea = new Area(new Rectangle2D.Double(x, y, width, height));
        PDRectangle crop = getPage().getCropBox();
        visibleArea.intersect(new Area(new Rectangle2D.Double(
                crop.getLowerLeftX(),
                crop.getLowerLeftY(),
                crop.getWidth(),
                crop.getHeight())));
        recordVisualRegion(
                annotation instanceof PDAnnotationWidget
                        ? VisualRegionType.FORM_FIELD
                        : VisualRegionType.ANNOTATION,
                visibleArea);
    }

    private void recordVisualRegion(VisualRegionType type, Area paintedArea) {
        if (paintedArea.isEmpty()) {
            return;
        }
        VisualRegion region = toDisplayRegion(
                type,
                getPage().getCropBox(),
                getPage().getRotation(),
                paintedArea.getBounds2D());
        if (region == null) {
            return;
        }
        visualRegionCounts.merge(type, 1L, Long::sum);
        List<VisualRegion> samples = visualRegions.get(type);
        if (samples.size() < MAX_VISUAL_REGION_SAMPLES) {
            samples.add(region);
        }
    }

    private static VisualRegion toDisplayRegion(
            VisualRegionType type,
            PDRectangle crop,
            int pageRotation,
            Rectangle2D pdfBounds
    ) {
        if (crop == null
                || pdfBounds == null
                || pdfBounds.isEmpty()
                || !Float.isFinite(crop.getLowerLeftX())
                || !Float.isFinite(crop.getLowerLeftY())
                || !Float.isFinite(crop.getWidth())
                || !Float.isFinite(crop.getHeight())
                || !Double.isFinite(pdfBounds.getMinX())
                || !Double.isFinite(pdfBounds.getMinY())
                || !Double.isFinite(pdfBounds.getWidth())
                || !Double.isFinite(pdfBounds.getHeight())
                || crop.getWidth() <= 0
                || crop.getHeight() <= 0) {
            return null;
        }
        int rotation = Math.floorMod(pageRotation, 360);
        if (rotation % 90 != 0) {
            return null;
        }
        double pdfX = pdfBounds.getMinX() - crop.getLowerLeftX();
        double pdfY = pdfBounds.getMinY() - crop.getLowerLeftY();
        double pdfWidth = pdfBounds.getWidth();
        double pdfHeight = pdfBounds.getHeight();
        double displayWidth = rotation == 90 || rotation == 270
                ? crop.getHeight() : crop.getWidth();
        double displayHeight = rotation == 90 || rotation == 270
                ? crop.getWidth() : crop.getHeight();
        double x;
        double y;
        double width;
        double height;
        switch (rotation) {
            case 0 -> {
                x = pdfX;
                y = displayHeight - (pdfY + pdfHeight);
                width = pdfWidth;
                height = pdfHeight;
            }
            case 90 -> {
                x = pdfY;
                y = pdfX;
                width = pdfHeight;
                height = pdfWidth;
            }
            case 180 -> {
                x = displayWidth - (pdfX + pdfWidth);
                y = pdfY;
                width = pdfWidth;
                height = pdfHeight;
            }
            case 270 -> {
                x = displayWidth - (pdfY + pdfHeight);
                y = displayHeight - (pdfX + pdfWidth);
                width = pdfHeight;
                height = pdfWidth;
            }
            default -> throw new IllegalStateException("Unexpected normalized page rotation");
        }
        double boundedX = Math.max(0, Math.min(x, displayWidth));
        double boundedY = Math.max(0, Math.min(y, displayHeight));
        double boundedWidth = Math.max(0, Math.min(width, displayWidth - boundedX));
        double boundedHeight = Math.max(0, Math.min(height, displayHeight - boundedY));
        if (boundedWidth <= 0 || boundedHeight <= 0) {
            return null;
        }
        double roundedX = rounded(boundedX);
        double roundedY = rounded(boundedY);
        double roundedWidth = rounded(boundedWidth);
        double roundedHeight = rounded(boundedHeight);
        if (!Double.isFinite(roundedX)
                || !Double.isFinite(roundedY)
                || !Double.isFinite(roundedWidth)
                || !Double.isFinite(roundedHeight)
                || roundedX < 0
                || roundedY < 0
                || roundedWidth <= 0
                || roundedHeight <= 0) {
            return null;
        }
        return new VisualRegion(
                type,
                roundedX,
                roundedY,
                roundedWidth,
                roundedHeight);
    }

    private VisualRegionAudit visualRegionAudit() {
        VisualRegionCounts counts = new VisualRegionCounts(
                visualRegionCounts.get(VisualRegionType.IMAGE),
                visualRegionCounts.get(VisualRegionType.ANNOTATION),
                visualRegionCounts.get(VisualRegionType.FORM_FIELD),
                visualRegionCounts.get(VisualRegionType.VECTOR_PATH));
        List<VisualRegion> samples = new ArrayList<>();
        for (VisualRegionType type : VisualRegionType.values()) {
            samples.addAll(visualRegions.get(type));
        }
        return VisualRegionAudit.of(counts, samples);
    }

    private static double rounded(double value) {
        return Math.rint(value * 1_000d) / 1_000d;
    }

    private void recordVisibleTextGridCell(double x, double y) {
        PDRectangle crop = getPage().getCropBox();
        if (crop.getWidth() <= 0 || crop.getHeight() <= 0) {
            return;
        }
        int column = Math.min(
                SPATIAL_GRID_SIZE - 1,
                (int) ((x - crop.getLowerLeftX()) / crop.getWidth() * SPATIAL_GRID_SIZE));
        int row = Math.min(
                SPATIAL_GRID_SIZE - 1,
                (int) ((y - crop.getLowerLeftY()) / crop.getHeight() * SPATIAL_GRID_SIZE));
        if (column >= 0 && row >= 0) {
            visibleTextGridCells.add(row * SPATIAL_GRID_SIZE + column);
        }
    }

    private GridCoverage gridCoverage() {
        int overlap = (int) imageGridCells.stream()
                .filter(visibleTextGridCells::contains)
                .count();
        double ratio = imageGridCells.isEmpty()
                ? 0
                : (double) overlap / imageGridCells.size();
        return new GridCoverage(imageGridCells.size(), overlap, ratio);
    }

    record PageEvidence(
            VisualContentAudit visualContent,
            GeometryVisibilityAudit geometryVisibility,
            AnnotationAppearanceAudit annotationAppearances,
            OptionalContentAudit optionalContent,
            VisualRegionAudit visualRegions
    ) {
    }

    private record GlyphLocation(long x, long y, int code, String fontName) {
    }

    private record GridCoverage(
            int imageCellCount,
            int imageTextOverlapCellCount,
            double imageTextOverlapRatio
    ) {
    }

    /**
     * Keeps exact image-union area while preventing one ever-growing Area from
     * making localized image mosaics progressively more expensive to merge.
     * The cells partition the page, so their union areas can be summed without
     * overlap or approximation.
     */
    private static final class AdaptiveAreaUnion {
        private final PDRectangle crop;
        private Area sequential = new Area();
        private TiledAreaUnion tiled;
        private int additions;

        private AdaptiveAreaUnion(PDRectangle crop) {
            this.crop = crop;
        }

        private void add(Area paintedArea) {
            additions++;
            if (tiled == null) {
                sequential.add(paintedArea);
                if (additions > TILED_IMAGE_UNION_THRESHOLD) {
                    tiled = new TiledAreaUnion(crop);
                    tiled.add(sequential);
                    sequential = null;
                }
                return;
            }
            tiled.add(paintedArea);
        }

        private double area() {
            return tiled == null
                    ? PageVisualAnalyzer.area(sequential)
                    : tiled.area();
        }
    }

    private static final class TiledAreaUnion {
        private final Rectangle2D.Double[] cells;
        private final Area[] unions;
        private final double originX;
        private final double originY;
        private final double cellWidth;
        private final double cellHeight;

        private TiledAreaUnion(PDRectangle crop) {
            cells = new Rectangle2D.Double[SPATIAL_GRID_SIZE * SPATIAL_GRID_SIZE];
            unions = new Area[cells.length];
            originX = crop.getLowerLeftX();
            originY = crop.getLowerLeftY();
            cellWidth = crop.getWidth() / SPATIAL_GRID_SIZE;
            cellHeight = crop.getHeight() / SPATIAL_GRID_SIZE;
            if (cellWidth <= 0 || cellHeight <= 0) {
                return;
            }
            for (int row = 0; row < SPATIAL_GRID_SIZE; row++) {
                for (int column = 0; column < SPATIAL_GRID_SIZE; column++) {
                    int index = row * SPATIAL_GRID_SIZE + column;
                    cells[index] = new Rectangle2D.Double(
                            originX + column * cellWidth,
                            originY + row * cellHeight,
                            cellWidth,
                            cellHeight);
                }
            }
        }

        private void add(Area paintedArea) {
            if (cellWidth <= 0 || cellHeight <= 0 || paintedArea.isEmpty()) {
                return;
            }
            Rectangle2D bounds = paintedArea.getBounds2D();
            int firstColumn = cellIndex(bounds.getMinX(), originX, cellWidth);
            int lastColumn = cellIndex(Math.nextDown(bounds.getMaxX()), originX, cellWidth);
            int firstRow = cellIndex(bounds.getMinY(), originY, cellHeight);
            int lastRow = cellIndex(Math.nextDown(bounds.getMaxY()), originY, cellHeight);
            for (int row = firstRow; row <= lastRow; row++) {
                for (int column = firstColumn; column <= lastColumn; column++) {
                    int index = row * SPATIAL_GRID_SIZE + column;
                    Rectangle2D cell = cells[index];
                    if (!paintedArea.intersects(cell)) {
                        continue;
                    }
                    Area clipped = new Area(paintedArea);
                    clipped.intersect(new Area(cell));
                    if (clipped.isEmpty()) {
                        continue;
                    }
                    if (unions[index] == null) {
                        unions[index] = clipped;
                    } else {
                        unions[index].add(clipped);
                    }
                }
            }
        }

        private double area() {
            double total = 0;
            for (Area union : unions) {
                if (union != null) {
                    total += PageVisualAnalyzer.area(union);
                }
            }
            return total;
        }

        private static int cellIndex(double coordinate, double origin, double size) {
            int index = (int) Math.floor((coordinate - origin) / size);
            return Math.max(0, Math.min(SPATIAL_GRID_SIZE - 1, index));
        }
    }
}
