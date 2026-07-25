package dev.putrenkov.pdfaudit;

import java.nio.file.Path;
import java.util.Objects;

record CliOptions(Mode mode, Path input, OutputFormat outputFormat) {
    static CliOptions parse(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");

        if (arguments.length == 1) {
            if ("--help".equals(arguments[0]) || "-h".equals(arguments[0])) {
                return new CliOptions(Mode.HELP, null, OutputFormat.TEXT);
            }
            if ("--version".equals(arguments[0])) {
                return new CliOptions(Mode.VERSION, null, OutputFormat.TEXT);
            }
        }

        boolean json = false;
        Path input = null;
        for (String argument : arguments) {
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
                json ? OutputFormat.JSON : OutputFormat.TEXT);
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
