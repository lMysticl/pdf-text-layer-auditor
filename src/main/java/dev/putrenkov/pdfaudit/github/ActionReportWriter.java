package dev.putrenkov.pdfaudit.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.putrenkov.pdfaudit.JsonReportPrinter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

final class ActionReportWriter {
    static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    ActionReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    void write(
            ActionRun run,
            GitHubContext context,
            Path reportPath
    ) throws IOException {
        WorkspacePaths.prepareOutputParent(context.workspace(), reportPath);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("repository", context.repository());
        root.put("pullRequestNumber", context.pullRequestNumber());
        root.put("filesChecked", run.filesChecked());
        root.put("filesWithFindings", run.filesWithFindings());
        root.put("filesFailed", run.filesFailed());
        root.put("conclusion", conclusion(run));

        ArrayNode files = root.putArray("files");
        for (ActionRun.AuditedPdf auditedPdf : run.files()) {
            ObjectNode file = files.addObject();
            file.put("path", auditedPdf.repositoryPath());
            if (auditedPdf.succeeded()) {
                file.put(
                        "status",
                        auditedPdf.report().needsAttention() ? "attention" : "passed");
                JsonNode report = objectMapper.readTree(
                        JsonReportPrinter.toJson(auditedPdf.report()));
                if (report instanceof ObjectNode reportObject) {
                    reportObject.put("file", auditedPdf.repositoryPath());
                }
                file.set("report", report);
            } else {
                file.put("status", "error");
                file.put("error", auditedPdf.error());
            }
        }

        byte[] content = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(root);
        Path temporary = Files.createTempFile(
                reportPath.getParent(),
                ".pdf-text-layer-audit-",
                ".json");
        try {
            Files.write(temporary, content);
            moveReplacing(temporary, reportPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String conclusion(ActionRun run) {
        return switch (run.exitCode()) {
            case 0 -> run.filesWithFindings() == 0 ? "passed" : "neutral";
            case 1 -> "findings";
            default -> "error";
        };
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
