package dev.putrenkov.pdfaudit;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        return run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, AuditWorkLimits.defaults());
    }

    static int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            AuditWorkLimits workLimits
    ) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        Objects.requireNonNull(workLimits, "workLimits");

        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException exception) {
            err.println(TerminalText.escape(exception.getMessage()));
            printUsage(err);
            return 2;
        }

        if (options.mode() == CliOptions.Mode.HELP) {
            printUsage(out);
            return completeOutput(out, err, 0);
        }
        if (options.mode() == CliOptions.Mode.VERSION) {
            out.println("pdf-text-layer-auditor " + version());
            return completeOutput(out, err, 0);
        }

        try {
            AuditReport report = new PdfTextLayerAuditor(
                    options.maxFileSizeBytes(),
                    options.maxPageCount(),
                    options.tinyTextThresholdPoints(),
                    workLimits)
                    .audit(options.input(), options.pageSelection());
            if (options.outputFormat() == CliOptions.OutputFormat.JSON) {
                new JsonReportPrinter().print(report, out);
            } else {
                new TextReportPrinter().print(report, out);
            }
            return completeOutput(out, err, report.needsAttention() ? 1 : 0);
        } catch (InvalidPasswordException exception) {
            err.println("Cannot audit a password-protected PDF without a password.");
            return 2;
        } catch (AuditWorkLimitException exception) {
            err.println("pdfTextLayerAuditorFailure=WORK_LIMIT_" + exception.code());
            err.println(TerminalText.escape(exception.getMessage()));
            return 2;
        } catch (IllegalArgumentException | SecurityException exception) {
            err.println(TerminalText.escape(exception.getMessage()));
            return 2;
        } catch (IOException exception) {
            err.println(
                    "Could not read PDF: " + TerminalText.escape(exception.getMessage()));
            return 2;
        }
    }

    private static int completeOutput(PrintStream out, PrintStream err, int successCode) {
        if (!out.checkError()) {
            return successCode;
        }
        err.println("Could not write command output.");
        return 2;
    }

    static String version() {
        String implementationVersion = Main.class.getPackage().getImplementationVersion();
        return implementationVersion == null ? "development" : implementationVersion;
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java -jar pdf-text-layer-auditor.jar [options] <file.pdf>");
        out.println();
        out.println("Options:");
        out.println("  --json                     Print a machine-readable JSON report");
        out.println("  --max-file-size-mib <MiB>  Set the input-size limit (default: 100)");
        out.println("  --max-pages <count>        Set the page-count limit (default: 1000)");
        out.println("  --tiny-text-threshold-pt <pt> Set threshold (default: 3; 0 disables)");
        out.println("  --pages <selection>        Inspect pages such as 1,3-5");
        out.println("  --version                  Show the installed version");
        out.println("  -h, --help                 Show this help");
        out.println();
        out.println("Exit codes:");
        out.println("  0  No basic text-layer problems detected");
        out.println("  1  One or more pages need attention");
        out.println("  2  Invalid arguments, audit failure, or output failure");
    }
}
