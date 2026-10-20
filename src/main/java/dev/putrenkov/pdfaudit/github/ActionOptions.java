package dev.putrenkov.pdfaudit.github;

import dev.putrenkov.pdfaudit.OptionValues;
import dev.putrenkov.pdfaudit.PdfTextLayerAuditor;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

final class ActionOptions {
    private final String token;
    private final boolean failOnFindings;
    private final int maxAnnotations;
    private final int maxPdfFiles;
    private final long maxTotalPdfBytes;
    private final long maxFileSizeBytes;
    private final int maxPageCount;
    private final float tinyTextThresholdPoints;
    private final Path reportPath;

    private ActionOptions(
            String token,
            boolean failOnFindings,
            int maxAnnotations,
            int maxPdfFiles,
            long maxTotalPdfBytes,
            long maxFileSizeBytes,
            int maxPageCount,
            float tinyTextThresholdPoints,
            Path reportPath
    ) {
        this.token = token;
        this.failOnFindings = failOnFindings;
        this.maxAnnotations = maxAnnotations;
        this.maxPdfFiles = maxPdfFiles;
        this.maxTotalPdfBytes = maxTotalPdfBytes;
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
        int maxAnnotations = OptionValues.nonNegativeInt(
                environment.getOrDefault("INPUT_MAX_ANNOTATIONS", "20"),
                "max_annotations");
        int maxPdfFiles = OptionValues.positiveInt(
                environment.getOrDefault("INPUT_MAX_FILES", "50"),
                "max_files");
        long maxTotalPdfBytes = OptionValues.mebibytes(
                environment.getOrDefault("INPUT_MAX_TOTAL_SIZE_MIB", "500"),
                "max_total_size_mib");
        long maxFileSizeBytes = OptionValues.mebibytes(
                environment.getOrDefault("INPUT_MAX_FILE_SIZE_MIB", "100"),
                "max_file_size_mib");
        int maxPageCount = OptionValues.positiveInt(
                environment.getOrDefault("INPUT_MAX_PAGES", "1000"),
                "max_pages");
        float tinyTextThresholdPoints = OptionValues.nonNegativeFloat(
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
                maxPdfFiles,
                maxTotalPdfBytes,
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

    int maxPdfFiles() {
        return maxPdfFiles;
    }

    long maxTotalPdfBytes() {
        return maxTotalPdfBytes;
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
}
