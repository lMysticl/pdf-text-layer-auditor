package dev.putrenkov.pdfaudit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class PageSelection {
    private static final PageSelection ALL = new PageSelection(List.of(), true);

    private final List<Range> ranges;
    private final boolean all;

    private PageSelection(List<Range> ranges, boolean all) {
        this.ranges = List.copyOf(ranges);
        this.all = all;
    }

    public static PageSelection all() {
        return ALL;
    }

    public static PageSelection parse(String specification) {
        if (specification == null || specification.isBlank()) {
            throw invalidSelection(specification);
        }

        List<Range> ranges = new ArrayList<>();
        for (String rawPart : specification.split(",", -1)) {
            String part = rawPart.trim();
            int separator = part.indexOf('-');
            if (separator < 0) {
                int page = parsePageNumber(part, specification);
                ranges.add(new Range(page, page));
                continue;
            }
            if (separator == 0
                    || separator == part.length() - 1
                    || part.indexOf('-', separator + 1) >= 0) {
                throw invalidSelection(specification);
            }

            int first = parsePageNumber(part.substring(0, separator).trim(), specification);
            int last = parsePageNumber(part.substring(separator + 1).trim(), specification);
            if (first > last) {
                throw invalidSelection(specification);
            }
            ranges.add(new Range(first, last));
        }

        return new PageSelection(ranges, false);
    }

    public List<Integer> resolve(int pageCount) {
        if (pageCount <= 0) {
            throw new IllegalArgumentException("pageCount must be positive");
        }
        if (all) {
            List<Integer> pages = new ArrayList<>(pageCount);
            for (int page = 1; page <= pageCount; page++) {
                pages.add(page);
            }
            return List.copyOf(pages);
        }

        TreeSet<Integer> pages = new TreeSet<>();
        for (Range range : ranges) {
            if (range.last() > pageCount) {
                throw new IllegalArgumentException(
                        "Requested page " + range.last()
                                + " exceeds document page count of " + pageCount);
            }
            for (int page = range.first(); page <= range.last(); page++) {
                pages.add(page);
            }
        }
        return List.copyOf(pages);
    }

    private static int parsePageNumber(String value, String specification) {
        try {
            int page = Integer.parseInt(value);
            if (page <= 0) {
                throw new NumberFormatException();
            }
            return page;
        } catch (NumberFormatException exception) {
            throw invalidSelection(specification);
        }
    }

    private static IllegalArgumentException invalidSelection(String specification) {
        return new IllegalArgumentException(
                "Invalid page selection: " + Objects.toString(specification, "<null>")
                        + " (expected values such as 1,3-5)");
    }

    private record Range(int first, int last) {
    }
}
