package dev.putrenkov.pdfaudit;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

record CliOptions(
        Mode mode,
        Path input,
        OutputFormat outputFormat,
        long maxFileSizeBytes,
        int maxPageCount,
        float tinyTextThresholdPoints,
        PageSelection pageSelection
) {
    private static final Set<String> SINGLE_USE_OPTIONS = Set.of(
            "--json",
            "--max-file-size-mib",
            "--max-pages",
            "--tiny-text-threshold-pt",
            "--pages");

    static CliOptions parse(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");

        if (arguments.length == 1) {
            if ("--help".equals(arguments[0]) || "-h".equals(arguments[0])) {
                return standalone(Mode.HELP);
            }
            if ("--version".equals(arguments[0])) {
                return standalone(Mode.VERSION);
            }
        }

        Set<String> seen = new HashSet<>();
        boolean json = false;
        long maxFileSizeBytes = PdfTextLayerAuditor.DEFAULT_MAX_FILE_SIZE_BYTES;
        int maxPageCount = PdfTextLayerAuditor.DEFAULT_MAX_PAGE_COUNT;
        float tinyTextThresholdPoints =
                PdfTextLayerAuditor.DEFAULT_TINY_TEXT_THRESHOLD_POINTS;
        PageSelection pageSelection = PageSelection.all();
        Path input = null;
        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            if (argument == null) {
                throw new IllegalArgumentException("Arguments must not be null");
            }

            if (SINGLE_USE_OPTIONS.contains(argument) && !seen.add(argument)) {
                throw new IllegalArgumentException(argument + " may only be specified once");
            }

            switch (argument) {
                case "--json" -> json = true;
                case "--max-file-size-mib" -> maxFileSizeBytes = OptionValues.mebibytes(
                        requireValue(arguments, ++index, argument), argument);
                case "--max-pages" -> maxPageCount = OptionValues.positiveInt(
                        requireValue(arguments, ++index, argument), argument);
                case "--tiny-text-threshold-pt" -> tinyTextThresholdPoints =
                        OptionValues.nonNegativeFloat(
                                requireValue(arguments, ++index, argument), argument);
                case "--pages" -> pageSelection =
                        PageSelection.parse(requireValue(arguments, ++index, argument));
                case "--help", "-h", "--version" ->
                        throw new IllegalArgumentException(argument + " must be used alone");
                default -> {
                    if (argument.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + argument);
                    }
                    if (input != null) {
                        throw new IllegalArgumentException("Only one PDF file may be specified");
                    }
                    input = Path.of(argument);
                }
            }
        }

        if (input == null) {
            throw new IllegalArgumentException("A PDF file is required");
        }

        return new CliOptions(
                Mode.AUDIT,
                input,
                json ? OutputFormat.JSON : OutputFormat.TEXT,
                maxFileSizeBytes,
                maxPageCount,
                tinyTextThresholdPoints,
                pageSelection);
    }

    private static CliOptions standalone(Mode mode) {
        return new CliOptions(
                mode,
                null,
                OutputFormat.TEXT,
                PdfTextLayerAuditor.DEFAULT_MAX_FILE_SIZE_BYTES,
                PdfTextLayerAuditor.DEFAULT_MAX_PAGE_COUNT,
                PdfTextLayerAuditor.DEFAULT_TINY_TEXT_THRESHOLD_POINTS,
                PageSelection.all());
    }

    private static String requireValue(String[] arguments, int index, String option) {
        if (index >= arguments.length || arguments[index] == null) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return arguments[index];
    }

    enum Mode {
        AUDIT,
        HELP,
        VERSION
    }

    enum OutputFormat {
        TEXT,
        JSON
    }
}
