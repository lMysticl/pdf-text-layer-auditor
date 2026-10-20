package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OptionValuesTest {
    @Test
    void acceptsValuesInsideTheirRanges() {
        assertEquals(1, OptionValues.positiveInt("1", "--max-pages"));
        assertEquals(0, OptionValues.nonNegativeInt("0", "max_annotations"));
        assertEquals(3L * 1024 * 1024, OptionValues.mebibytes("3", "--max-file-size-mib"));
        assertEquals(0f, OptionValues.nonNegativeFloat("0", "--tiny-text-threshold-pt"));
    }

    @Test
    void rejectsOutOfRangeValuesWithTheSameMessageAsUnparseableOnes() {
        IllegalArgumentException zero = assertThrows(
                IllegalArgumentException.class,
                () -> OptionValues.positiveInt("0", "--max-pages"));
        IllegalArgumentException text = assertThrows(
                IllegalArgumentException.class,
                () -> OptionValues.positiveInt("ten", "--max-pages"));
        assertEquals(
                "--max-pages must be a positive integer within the supported range",
                zero.getMessage());
        assertEquals(zero.getMessage(), text.getMessage());
        assertNull(zero.getCause());
        assertInstanceOf(NumberFormatException.class, text.getCause());

        IllegalArgumentException negative = assertThrows(
                IllegalArgumentException.class,
                () -> OptionValues.nonNegativeInt("-1", "max_annotations"));
        assertEquals(
                "max_annotations must be a non-negative integer within the supported range",
                negative.getMessage());
    }

    @Test
    void rejectsMebibytesThatOverflowBytes() {
        IllegalArgumentException overflow = assertThrows(
                IllegalArgumentException.class,
                () -> OptionValues.mebibytes(Long.toString(Long.MAX_VALUE), "max_total_size_mib"));
        assertEquals(
                "max_total_size_mib must be a positive integer within the supported range",
                overflow.getMessage());
        assertInstanceOf(ArithmeticException.class, overflow.getCause());
    }

    @Test
    void rejectsNonFiniteThresholds() {
        for (String value : new String[] {"NaN", "Infinity", "-0.5", "small"}) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> OptionValues.nonNegativeFloat(value, "--tiny-text-threshold-pt"));
            assertEquals(
                    "--tiny-text-threshold-pt must be a finite, non-negative number",
                    failure.getMessage());
        }
    }
}
