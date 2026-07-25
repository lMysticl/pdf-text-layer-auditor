package dev.putrenkov.pdfaudit;

import java.io.IOException;
import java.io.PrintStream;
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
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            printUsage(System.err);
            return 2;
        }

        if (options.mode() == CliOptions.Mode.HELP) {
            printUsage(System.out);
            return 0;
        }
        if (options.mode() == CliOptions.Mode.VERSION) {
            System.out.println("pdf-text-layer-auditor " + version());
            return 0;
        }

        try {
            AuditReport report = new PdfTextLayerAuditor().audit(options.input());
            if (options.outputFormat() == CliOptions.OutputFormat.JSON) {
                new JsonReportPrinter().print(report, System.out);
            } else {
                new TextReportPrinter().print(report, System.out);
            }
            return report.needsAttention() ? 1 : 0;
        } catch (InvalidPasswordException exception) {
            System.err.println("Cannot audit a password-protected PDF without a password.");
            return 2;
        } catch (IllegalArgumentException | SecurityException exception) {
            System.err.println(exception.getMessage());
            return 2;
        } catch (IOException exception) {
            System.err.println("Could not read PDF: " + exception.getMessage());
            return 2;
        }
    }

    static String version() {
        String implementationVersion = Main.class.getPackage().getImplementationVersion();
        return implementationVersion == null ? "development" : implementationVersion;
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java -jar pdf-text-layer-auditor.jar [--json] <file.pdf>");
        out.println();
        out.println("Options:");
        out.println("  --json     Print a machine-readable JSON report");
        out.println("  --version  Show the installed version");
        out.println("  -h, --help Show this help");
        out.println();
        out.println("Exit codes:");
        out.println("  0  No basic text-layer problems detected");
        out.println("  1  One or more pages need attention");
        out.println("  2  Invalid arguments or the PDF could not be audited");
    }
}
