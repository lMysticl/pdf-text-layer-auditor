package dev.putrenkov.pdfaudit.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GitHubActionMain {
    private GitHubActionMain() {
    }

    public static void main(String[] arguments) {
        int exitCode;
        try {
            Map<String, String> environment = environmentWithInputs(
                    System.getenv(),
                    arguments);
            exitCode = run(environment, System.out, System.err);
        } catch (IllegalArgumentException exception) {
            System.err.println("PDF Text Layer Audit failed: " + safeMessage(exception));
            exitCode = 2;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static Map<String, String> environmentWithInputs(
            Map<String, String> environment,
            String[] arguments
    ) {
        if (arguments.length == 0) {
            return environment;
        }
        if (arguments.length != 9) {
            throw new IllegalArgumentException(
                    "Expected 9 Docker action input arguments, received "
                            + arguments.length);
        }

        Map<String, String> combined = new HashMap<>(environment);
        combined.put("INPUT_TOKEN", arguments[0]);
        combined.put("INPUT_FAIL_ON_FINDINGS", arguments[1]);
        combined.put("INPUT_MAX_ANNOTATIONS", arguments[2]);
        combined.put("INPUT_MAX_FILES", arguments[3]);
        combined.put("INPUT_MAX_TOTAL_SIZE_MIB", arguments[4]);
        combined.put("INPUT_MAX_FILE_SIZE_MIB", arguments[5]);
        combined.put("INPUT_MAX_PAGES", arguments[6]);
        combined.put("INPUT_TINY_TEXT_THRESHOLD_PT", arguments[7]);
        combined.put("INPUT_REPORT_PATH", arguments[8]);
        return Map.copyOf(combined);
    }

    static int run(
            Map<String, String> environment,
            PrintStream out,
            PrintStream err
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            ActionOptions options = ActionOptions.fromEnvironment(environment);
            GitHubContext context = GitHubContext.fromEnvironment(
                    environment,
                    objectMapper);
            List<PullRequestFile> pullRequestFiles =
                    GitHubPullRequestFilesClient.create(objectMapper)
                            .listFiles(context, options.token());
            List<ChangedPdfSelector.ChangedPdf> changedPdfs =
                    new ChangedPdfSelector().select(
                            pullRequestFiles,
                            context.workspace());
            ActionWorkloadPolicy.validate(
                    changedPdfs,
                    options.maxPdfFiles(),
                    options.maxTotalPdfBytes());

            ActionRun run = new GitHubActionRunner(options.createAuditor()::audit)
                    .run(changedPdfs, options.failOnFindings());
            new ActionReportWriter(objectMapper).write(
                    run,
                    context,
                    options.reportPath());
            new GitHubWorkflowReporter(environment, out).publish(run, options);
            return run.exitCode();
        } catch (IllegalArgumentException | IOException exception) {
            err.println("PDF Text Layer Audit failed: " + safeMessage(exception));
            return 2;
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replace('\r', ' ').replace('\n', ' ').strip();
    }
}
