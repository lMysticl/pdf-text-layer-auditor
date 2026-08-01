package dev.putrenkov.pdfaudit;

import java.util.List;

public record UnicodeProfileAudit(
        List<String> scripts,
        int rightToLeftCharacterCount,
        int combiningMarkCount,
        int nonBmpCharacterCount,
        int variationSelectorCount,
        int zeroWidthJoinerCount,
        int bidiControlCount
) {
    public UnicodeProfileAudit {
        scripts = List.copyOf(scripts);
        if (rightToLeftCharacterCount < 0
                || combiningMarkCount < 0
                || nonBmpCharacterCount < 0
                || variationSelectorCount < 0
                || zeroWidthJoinerCount < 0
                || bidiControlCount < 0) {
            throw new IllegalArgumentException("Unicode profile counters are invalid");
        }
        if (scripts.stream().anyMatch(script -> script == null || script.isBlank())
                || scripts.stream().distinct().count() != scripts.size()
                || !scripts.equals(scripts.stream().sorted().toList())) {
            throw new IllegalArgumentException(
                    "Unicode script names must be unique, non-blank, and sorted");
        }
    }
}
