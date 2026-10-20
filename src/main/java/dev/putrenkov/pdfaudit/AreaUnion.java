package dev.putrenkov.pdfaudit;

import java.awt.geom.Area;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * The union of painted shapes on one page. After many additions it switches to a
 * grid of per-tile unions so the cost of each addition stays bounded.
 */
final class AreaUnion {
    private static final int TILED_THRESHOLD = 64;
    private static final int GRID_SIZE = 8;

    private final PDRectangle crop;
    private Area sequential = new Area();
    private Tiles tiled;
    private int additions;

    AreaUnion(PDRectangle crop) {
        this.crop = crop;
    }

    void add(Area paintedArea) {
        additions++;
        if (tiled == null) {
            sequential.add(paintedArea);
            if (additions > TILED_THRESHOLD) {
                tiled = new Tiles(crop);
                tiled.add(sequential);
                sequential = null;
            }
            return;
        }
        tiled.add(paintedArea);
    }

    double area() {
        return tiled == null
                ? areaOf(sequential)
                : tiled.area();
    }

    /** Area enclosed by a shape, from its flattened outline (shoelace formula). */
    static double areaOf(Area area) {
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

    private static final class Tiles {
        private final Rectangle2D.Double[] cells;
        private final Area[] unions;
        private final double originX;
        private final double originY;
        private final double cellWidth;
        private final double cellHeight;

        private Tiles(PDRectangle crop) {
            cells = new Rectangle2D.Double[GRID_SIZE * GRID_SIZE];
            unions = new Area[cells.length];
            originX = crop.getLowerLeftX();
            originY = crop.getLowerLeftY();
            cellWidth = crop.getWidth() / GRID_SIZE;
            cellHeight = crop.getHeight() / GRID_SIZE;
            if (cellWidth <= 0 || cellHeight <= 0) {
                return;
            }
            for (int row = 0; row < GRID_SIZE; row++) {
                for (int column = 0; column < GRID_SIZE; column++) {
                    int index = row * GRID_SIZE + column;
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
                    int index = row * GRID_SIZE + column;
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
                    total += areaOf(union);
                }
            }
            return total;
        }

        private static int cellIndex(double coordinate, double origin, double size) {
            int index = (int) Math.floor((coordinate - origin) / size);
            return Math.max(0, Math.min(GRID_SIZE - 1, index));
        }
    }
}
