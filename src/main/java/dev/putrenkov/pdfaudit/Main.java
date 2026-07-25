package dev.putrenkov.pdfaudit;

import java.io.IOException;
import java.nio.file.Path;
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
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            printUsage();
            return 0;
        }

        boolean jsonOutput = args.length == 2 && "--json".equals(args[0]);
        if (args.length != 1 && !jsonOutput) {
            printUsage();
            return 2;
        }

        try {
            String fileArgument = jsonOutput ? args[1] : args[0];
            AuditReport report = new PdfTextLayerAuditor().audit(Path.of(fileArgument));
            if (jsonOutput) {
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

    private static void printUsage() {
        System.out.println("Usage: java -jar pdf-text-layer-auditor.jar <file.pdf>");
        System.out.println("       java -jar pdf-text-layer-auditor.jar --json <file.pdf>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --json  Print a machine-readable JSON report");
        System.out.println("  -h, --help  Show this help");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  No basic text-layer problems detected");
        System.out.println("  1  One or more pages need attention");
        System.out.println("  2  Invalid arguments or the PDF could not be audited");
    }
}
