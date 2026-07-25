package dev.putrenkov.pdfaudit;

import java.util.Locale;

final class TerminalText {
    private TerminalText() {
    }

    static String escape(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEachOrdered(codePoint -> {
            if (requiresEscaping(codePoint)) {
                String format = codePoint <= Character.MAX_VALUE ? "\\u%04X" : "\\U%08X";
                escaped.append(String.format(Locale.ROOT, format, codePoint));
            } else {
                escaped.appendCodePoint(codePoint);
            }
        });
        return escaped.toString();
    }

    private static boolean requiresEscaping(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.SURROGATE;
    }
}
