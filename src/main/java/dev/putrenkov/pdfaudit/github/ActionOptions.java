package dev.putrenkov.pdfaudit.github;

import dev.putrenkov.pdfaudit.PdfTextLayerAuditor;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

final class ActionOptions {
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024;

    private final String token;
    private final boolean failOnFindings;
    private final int maxAnnotations;
    private final long maxFileSizeBytes;
    private final int maxPageCount;
    private final float tinyTextThresholdPoints;
    private final Path reportPath;

    private ActionOptions(
            String token,
            boolean failOnFindings,
            int maxAnnotations,
            long maxFileSizeBytes,
            int maxPageCount,
            float tinyTextThresholdPoints,
            Path reportPath
    ) {
        this.token = token;
        this.failOnFindings = failOnFindings;
        this.maxAnnotations = maxAnnotations;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxPageCount = maxPageCount;
        this.tinyTextThresholdPoints = tinyTextThresholdPoints;
        this.reportPath = reportPath;
    }

    static ActionOptions fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");

        String token = required(environment, "INPUT_TOKEN");
        boolean failOnFindings = parseBoolean(
                environment.getOrDefault("INPUT_FAIL_ON_FINDINGS", "true"),
                "fail_on_findings");
        int maxAnnotations = parseNonNegativeInt(
                environment.getOrDefault("INPUT_MAX_ANNOTATIONS", "20"),
                "max_annotations");
        long maxFileSizeBytes = parseMebibytes(
                environment.getOrDefault("INPUT_MAX_FILE_SIZE_MIB", "100"),
                "max_file_size_mib");
        int maxPageCount = parsePositiveInt(
                environment.getOrDefault("INPUT_MAX_PAGES", "1000"),
                "max_pages");
        float tinyTextThresholdPoints = parseNonNegativeFloat(
                environment.getOrDefault("INPUT_TINY_TEXT_THRESHOLD_PT", "3"),
                "tiny_text_threshold_pt");

        Path workspace = Path.of(required(environment, "GITHUB_WORKSPACE"))
                .toAbsolutePath()
                .normalize();
        Path reportPath = WorkspacePaths.resolveOutput(
                workspace,
                environment.getOrDefault(
                        "INPUT_REPORT_PATH",
                        "pdf-text-layer-audit.json"));

        return new ActionOptions(
                token,
                failOnFindings,
                maxAnnotations,
                maxFileSizeBytes,
                maxPageCount,
                tinyTextThresholdPoints,
                reportPath);
    }

    String token() {
        return token;
    }

    boolean failOnFindings() {
        return failOnFindings;
    }

    int maxAnnotations() {
        return maxAnnotations;
    }

    long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    int maxPageCount() {
        return maxPageCount;
    }

    float tinyTextThresholdPoints() {
        return tinyTextThresholdPoints;
    }

    Path reportPath() {
        return reportPath;
    }

    PdfTextLayerAuditor createAuditor() {
        return new PdfTextLayerAuditor(
                maxFileSizeBytes,
                maxPageCount,
                tinyTextThresholdPoints);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required environment value: " + name);
        }
        return value;
    }

    private static boolean parseBoolean(String value, String input) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(input + " must be true or false");
    }

    private static int parseNonNegativeInt(String value, String input) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    input + " must be a non-negative integer within the supported range");
        }
    }

    private static int parsePositiveInt(String value, String input) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    input + " must be a positive integer within the supported range");
        }
    }

    private static long parseMebibytes(String value, String input) {
        try {
            long mebibytes = Long.parseLong(value);
            if (mebibytes <= 0) {
                throw new NumberFormatException();
            }
            return Math.multiplyExact(mebibytes, BYTES_PER_MEBIBYTE);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    input + " must be a positive integer within the supported range");
        }
    }

    private static float parseNonNegativeFloat(String value, String input) {
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed) || parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    input + " must be a finite, non-negative number");
        }
    }
}
