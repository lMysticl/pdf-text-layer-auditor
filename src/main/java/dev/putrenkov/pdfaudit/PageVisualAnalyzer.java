package dev.putrenkov.pdfaudit;

import java.awt.geom.Area;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentMembershipDictionary;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
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
    private static final COSName OPTIONAL_CONTENT = COSName.getPDFName("OC");
    private static final COSName OPTIONAL_CONTENT_GROUP = COSName.getPDFName("OCG");
    private static final COSName OPTIONAL_CONTENT_MEMBERSHIP = COSName.getPDFName("OCMD");

    private final GeneralPath currentPath = new GeneralPath();
    private final PDOptionalContentProperties optionalContentProperties;
    private final AuditWorkLimits workLimits;
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

    private PageVisualAnalyzer(
            PDPage page,
            PDOptionalContentProperties optionalContentProperties,
            AuditWorkLimits workLimits
    ) {
        super(page);
        this.optionalContentProperties = optionalContentProperties;
        this.workLimits = workLimits;
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
                analyzer.processAnnotationAppearances(annotation);
            }
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
                            analyzer.optionalContentEvaluationFailureCount)));
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
        if (OPTIONAL_CONTENT.equals(tag) || isOptionalContent(properties)) {
            optionalContentPresent = true;
            recordOptionalContent(properties == null
                    ? null
                    : PDPropertyList.create(properties));
        }
        super.beginMarkedContentSequence(tag, properties);
    }

    @Override
    public void drawImage(PDImage image) {
        imageCount++;
        if (imageCount > workLimits.maximumImageCount()) {
            throw new AuditWorkLimitException(
                    AuditWorkLimitException.Code.IMAGE_COUNT,
                    workLimits.maximumImageCount());
        }
        if (image instanceof PDImageXObject imageXObject
                && imageXObject.getOptionalContent() != null) {
            optionalContentPresent = true;
            recordOptionalContent(imageXObject.getOptionalContent());
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
    public void showForm(PDFormXObject form) throws IOException {
        if (form.getOptionalContent() != null) {
            optionalContentPresent = true;
            recordOptionalContent(form.getOptionalContent());
        }
        super.showForm(form);
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
        recordPaintedVectorOperation();
    }

    private void recordPaintedPath() {
        if (!currentPath.getPathIterator(null).isDone()) {
            recordPaintedVectorOperation();
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
        return Math.max(0, Math.min(1, area(combinedImageArea) / pageAreaValue));
    }

    record PageEvidence(
            VisualContentAudit visualContent,
            GeometryVisibilityAudit geometryVisibility,
            AnnotationAppearanceAudit annotationAppearances,
            OptionalContentAudit optionalContent
    ) {
    }

    private record GlyphLocation(long x, long y, int code, String fontName) {
    }
}
