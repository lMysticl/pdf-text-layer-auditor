package dev.putrenkov.pdfaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class VisualRegionAuditTest {

    @Test
    void rejectsInvalidRegionCoordinatesAndMissingType() {
        assertThrows(NullPointerException.class,
                () -> new VisualRegion(null, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegion(VisualRegionType.IMAGE, Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegion(VisualRegionType.IMAGE, 0, Double.NaN, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegion(VisualRegionType.IMAGE, -1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegion(VisualRegionType.IMAGE, 0, -1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegion(VisualRegionType.IMAGE, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegion(VisualRegionType.IMAGE, 0, 0, 1, 0));
    }

    @Test
    void enforcesExactCountAndTruncationInvariants() {
        VisualRegion region = new VisualRegion(VisualRegionType.IMAGE, 0, 0, 1, 1);
        VisualRegionCounts oneImage = new VisualRegionCounts(1, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegionAudit(-1, false, VisualRegionCounts.empty(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegionAudit(0, false, oneImage, List.of(region)));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegionAudit(1, true, oneImage, List.of(region)));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegionAudit(2, false, oneImage, List.of(region)));
        assertThrows(IllegalArgumentException.class,
                () -> new VisualRegionAudit(1, false,
                        new VisualRegionCounts(0, 1, 0), List.of(region)));
        assertEquals(VisualRegionAudit.empty(),
                VisualRegionAudit.of(VisualRegionCounts.empty(), List.of()));
        assertEquals(1, VisualRegionAudit.of(oneImage, List.of(region)).regions().size());
    }

    @Test
    void unassessedSpatialEvidenceCannotContainVisualRegions() {
        VisualRegionAudit visualRegions = VisualRegionAudit.of(
                new VisualRegionCounts(1, 0, 0),
                List.of(new VisualRegion(VisualRegionType.IMAGE, 0, 0, 1, 1)));

        assertThrows(IllegalArgumentException.class, () -> new SpatialEvidenceAudit(
                false, null, null, null, null, 0, false, List.of(), visualRegions));
        assertThrows(IllegalArgumentException.class, () -> new SpatialEvidenceAudit(
                true, 1.0, 1.0, 0, SpatialEvidenceAudit.TOP_LEFT_DISPLAY_POINTS,
                0, false, List.of(), null));
    }
}
