package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageSelectionTest {
    @Test
    void resolvesRangesInDocumentOrderAndRemovesDuplicates() {
        assertEquals(
                List.of(1, 3, 4, 5, 7),
                PageSelection.parse("7,3-5,1,4").resolve(10));
    }

    @Test
    void allSelectsEveryPage() {
        assertEquals(List.of(1, 2, 3), PageSelection.all().resolve(3));
    }

    @Test
    void rejectsMalformedSelections() {
        for (String selection : new String[] {
            "", " ", "0", "-1", "1-", "-3", "3-1", "1,,2", "1-2-3", "text"
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PageSelection.parse(selection));
        }
    }

    @Test
    void rejectsPagesOutsideDocument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PageSelection.parse("1,5").resolve(4));

        assertEquals(
                "Requested page 5 exceeds document page count of 4",
                exception.getMessage());
    }
}
