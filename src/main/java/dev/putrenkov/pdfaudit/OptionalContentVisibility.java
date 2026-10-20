package dev.putrenkov.pdfaudit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentMembershipDictionary;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.rendering.RenderDestination;

/**
 * Decides whether optional content is hidden for a destination: groups, membership
 * policies and visibility expressions, with cycle and depth guards for malformed files.
 */
final class OptionalContentVisibility {
    private final PDOptionalContentProperties properties;

    OptionalContentVisibility(PDOptionalContentProperties properties) {
        this.properties = properties;
    }

    /** True or false when decided; null when the PDF does not allow a decision. */
    Boolean hidden(PDPropertyList propertyList, RenderDestination destination) {
        return isHidden(propertyList, destination, 0, identitySet());
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
                return properties != null
                        && !properties.isGroupEnabled(group);
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
}
