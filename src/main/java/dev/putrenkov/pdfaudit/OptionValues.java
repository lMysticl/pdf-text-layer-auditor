package dev.putrenkov.pdfaudit;

/**
 * Parses the limits shared by the command line and the GitHub Action, so both reject the same
 * values with the same messages.
 */
public final class OptionValues {
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024;

    private OptionValues() {
    }

    public static int positiveInt(String value, String name) {
        int parsed = integer(value, name, "positive");
        if (parsed <= 0) {
            throw outOfRange(name, "positive", null);
        }
        return parsed;
    }

    public static int nonNegativeInt(String value, String name) {
        int parsed = integer(value, name, "non-negative");
        if (parsed < 0) {
            throw outOfRange(name, "non-negative", null);
        }
        return parsed;
    }

    /** A positive whole number of mebibytes, returned in bytes. */
    public static long mebibytes(String value, String name) {
        try {
            long mebibytes = Long.parseLong(value);
            if (mebibytes <= 0) {
                throw outOfRange(name, "positive", null);
            }
            return Math.multiplyExact(mebibytes, BYTES_PER_MEBIBYTE);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw outOfRange(name, "positive", exception);
        }
    }

    public static float nonNegativeFloat(String value, String name) {
        try {
            float parsed = Float.parseFloat(value);
            if (Float.isFinite(parsed) && parsed >= 0) {
                return parsed;
            }
            throw new IllegalArgumentException(name + " must be a finite, non-negative number");
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    name + " must be a finite, non-negative number", exception);
        }
    }

    private static int integer(String value, String name, String sign) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw outOfRange(name, sign, exception);
        }
    }

    private static IllegalArgumentException outOfRange(String name, String sign, Exception cause) {
        return new IllegalArgumentException(
                name + " must be a " + sign + " integer within the supported range", cause);
    }
}
