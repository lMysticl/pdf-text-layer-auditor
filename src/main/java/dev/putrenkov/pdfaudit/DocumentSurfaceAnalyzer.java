package dev.putrenkov.pdfaudit;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;

final class DocumentSurfaceAnalyzer {
    private static final COSName ACRO_FORM = COSName.getPDFName("AcroForm");
    private static final COSName XFA = COSName.getPDFName("XFA");
    private static final COSName EMBEDDED_FILES = COSName.getPDFName("EmbeddedFiles");
    private static final COSName COLLECTION = COSName.getPDFName("Collection");
    private static final COSName ASSOCIATED_FILES = COSName.getPDFName("AF");
    private static final COSName SIGNATURE = COSName.getPDFName("Sig");
    private static final int MAXIMUM_TREE_DEPTH = 64;

    private final int maximumSurfaceCount;
    private int visitedSurfaceCount;
    private boolean complete = true;

    private DocumentSurfaceAnalyzer(int maximumSurfaceCount) {
        this.maximumSurfaceCount = maximumSurfaceCount;
    }

    static DocumentSurfaceAudit analyze(PDDocument document, AuditWorkLimits limits) {
        DocumentSurfaceAnalyzer analyzer =
                new DocumentSurfaceAnalyzer(limits.maximumDocumentSurfaceCount());
        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        FormCounts forms = analyzer.countFormFields(
                catalog.getCOSDictionary(ACRO_FORM));
        int embeddedFiles = analyzer.countEmbeddedFiles(catalog);
        int associatedFiles = analyzer.countAssociatedFiles(catalog);
        return new DocumentSurfaceAudit(
                true,
                analyzer.complete,
                forms.fieldCount(),
                forms.signatureCount(),
                forms.widgetWithoutAppearanceCount(),
                forms.xfaPresent(),
                embeddedFiles,
                associatedFiles,
                catalog.containsKey(COLLECTION));
    }

    private FormCounts countFormFields(COSDictionary acroForm) {
        if (acroForm == null) {
            return new FormCounts(0, 0, 0, false);
        }
        COSArray fields = acroForm.getCOSArray(COSName.FIELDS);
        if (fields == null) {
            complete = false;
            return new FormCounts(0, 0, 0, acroForm.containsKey(XFA));
        }
        ArrayDeque<FieldNode> pending = new ArrayDeque<>();
        for (int index = 0; index < fields.size(); index++) {
            COSBase value = fields.getObject(index);
            if (value instanceof COSDictionary field) {
                pending.add(new FieldNode(field, null, 0));
            } else {
                complete = false;
            }
        }
        Set<COSBase> visited = identitySet();
        int fieldCount = 0;
        int signatureCount = 0;
        int widgetWithoutAppearanceCount = 0;
        while (!pending.isEmpty()) {
            FieldNode node = pending.removeFirst();
            if (node.depth() > MAXIMUM_TREE_DEPTH || !visited.add(node.dictionary())) {
                complete = false;
                continue;
            }
            recordSurface();
            fieldCount++;
            COSName fieldType = node.dictionary().getCOSName(COSName.FT);
            if (fieldType == null) {
                fieldType = node.inheritedFieldType();
            }
            if (SIGNATURE.equals(fieldType)) {
                signatureCount++;
            }
            if (COSName.WIDGET.equals(node.dictionary().getCOSName(COSName.SUBTYPE))
                    && !node.dictionary().containsKey(COSName.AP)) {
                widgetWithoutAppearanceCount++;
            }
            COSArray kids = node.dictionary().getCOSArray(COSName.KIDS);
            if (kids != null) {
                for (int index = 0; index < kids.size(); index++) {
                    COSBase value = kids.getObject(index);
                    if (value instanceof COSDictionary child) {
                        pending.add(new FieldNode(child, fieldType, node.depth() + 1));
                    } else {
                        complete = false;
                    }
                }
            }
        }
        return new FormCounts(
                fieldCount,
                signatureCount,
                widgetWithoutAppearanceCount,
                acroForm.containsKey(XFA));
    }

    private int countEmbeddedFiles(COSDictionary catalog) {
        COSDictionary names = catalog.getCOSDictionary(COSName.NAMES);
        if (names == null) {
            return 0;
        }
        COSDictionary root = names.getCOSDictionary(EMBEDDED_FILES);
        if (root == null) {
            return 0;
        }
        ArrayDeque<TreeNode> pending = new ArrayDeque<>();
        pending.add(new TreeNode(root, 0));
        Set<COSBase> visited = identitySet();
        int count = 0;
        while (!pending.isEmpty()) {
            TreeNode node = pending.removeFirst();
            if (node.depth() > MAXIMUM_TREE_DEPTH || !visited.add(node.dictionary())) {
                complete = false;
                continue;
            }
            COSArray entries = node.dictionary().getCOSArray(COSName.NAMES);
            if (entries != null) {
                if (entries.size() % 2 != 0) {
                    complete = false;
                }
                for (int index = 0; index + 1 < entries.size(); index += 2) {
                    recordSurface();
                    count++;
                }
            }
            COSArray kids = node.dictionary().getCOSArray(COSName.KIDS);
            if (kids != null) {
                for (int index = 0; index < kids.size(); index++) {
                    COSBase value = kids.getObject(index);
                    if (value instanceof COSDictionary child) {
                        pending.add(new TreeNode(child, node.depth() + 1));
                    } else {
                        complete = false;
                    }
                }
            }
        }
        return count;
    }

    private int countAssociatedFiles(COSDictionary catalog) {
        COSArray files = catalog.getCOSArray(ASSOCIATED_FILES);
        if (files == null) {
            return 0;
        }
        for (int index = 0; index < files.size(); index++) {
            recordSurface();
            if (!(files.getObject(index) instanceof COSDictionary)) {
                complete = false;
            }
        }
        return files.size();
    }

    private void recordSurface() {
        visitedSurfaceCount++;
        if (visitedSurfaceCount > maximumSurfaceCount) {
            throw new AuditWorkLimitException(
                    AuditWorkLimitException.Code.DOCUMENT_SURFACE_COUNT,
                    maximumSurfaceCount);
        }
    }

    private static Set<COSBase> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private record FieldNode(COSDictionary dictionary, COSName inheritedFieldType, int depth) {
    }

    private record TreeNode(COSDictionary dictionary, int depth) {
    }

    private record FormCounts(
            int fieldCount,
            int signatureCount,
            int widgetWithoutAppearanceCount,
            boolean xfaPresent
    ) {
    }
}
