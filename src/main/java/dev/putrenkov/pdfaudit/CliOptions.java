package dev.putrenkov.pdfaudit;

import java.nio.file.Path;
import java.util.Objects;

record CliOptions(
        Mode mode,
        Path input,
        OutputFormat outputFormat,
        long maxFileSizeBytes,
        int maxPageCount
) {
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024;

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

        boolean json = false;
        boolean maxFileSizeSpecified = false;
        boolean maxPageCountSpecified = false;
        long maxFileSizeBytes = PdfTextLayerAuditor.DEFAULT_MAX_FILE_SIZE_BYTES;
        int maxPageCount = PdfTextLayerAuditor.DEFAULT_MAX_PAGE_COUNT;
        Path input = null;
        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            if (argument == null) {
                throw new IllegalArgumentException("Arguments must not be null");
            }

            switch (argument) {
                case "--json" -> {
                    if (json) {
                        throw new IllegalArgumentException("--json may only be specified once");
                    }
                    json = true;
                }
                case "--max-file-size-mib" -> {
                    if (maxFileSizeSpecified) {
                        throw new IllegalArgumentException(
                                "--max-file-size-mib may only be specified once");
                    }
                    String value = requireValue(arguments, ++index, argument);
                    maxFileSizeBytes = parseMebibytes(value, argument);
                    maxFileSizeSpecified = true;
                }
                case "--max-pages" -> {
                    if (maxPageCountSpecified) {
                        throw new IllegalArgumentException(
                                "--max-pages may only be specified once");
                    }
                    String value = requireValue(arguments, ++index, argument);
                    maxPageCount = parsePositiveInt(value, argument);
                    maxPageCountSpecified = true;
                }
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
                maxPageCount);
    }

    private static CliOptions standalone(Mode mode) {
        return new CliOptions(
                mode,
                null,
                OutputFormat.TEXT,
                PdfTextLayerAuditor.DEFAULT_MAX_FILE_SIZE_BYTES,
                PdfTextLayerAuditor.DEFAULT_MAX_PAGE_COUNT);
    }

    private static String requireValue(String[] arguments, int index, String option) {
        if (index >= arguments.length || arguments[index] == null) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return arguments[index];
    }

    private static long parseMebibytes(String value, String option) {
        try {
            long mebibytes = Long.parseLong(value);
            if (mebibytes <= 0) {
                throw new NumberFormatException();
            }
            return Math.multiplyExact(mebibytes, BYTES_PER_MEBIBYTE);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    option + " must be a positive integer within the supported range");
        }
    }

    private static int parsePositiveInt(String value, String option) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    option + " must be a positive integer within the supported range");
        }
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
