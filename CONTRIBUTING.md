# Contributing

Contributions should improve a reproducible PDF text-layer workflow rather than only increase coverage numbers.

## Before opening an issue

1. Reproduce the behavior with the latest release.
2. Record the output of `java -jar pdf-text-layer-auditor.jar --version`.
3. Run the smallest command that demonstrates the problem.
4. Check whether the result is already covered by the limitations in the README.

Never attach a confidential, customer, legal, financial, or medical PDF. Prefer a synthetic document created in a test. If a real document is essential, remove content and metadata outside the repository and verify that the redacted file still reproduces the behavior.

## Development setup

Requirements:

- Java 21 or newer
- Maven 3.9 or newer
- Docker, when changing the GitHub Action packaging

Run the full build:

```bash
mvn clean verify
```

When action metadata, GitHub integration code, or runtime dependencies change,
also build the container:

```bash
docker build -t pdf-text-layer-audit-action .
```

Run the executable:

```bash
java -jar target/pdf-text-layer-auditor.jar --help
```

Tests should create compact PDFs at runtime with PDFBox. Add a binary fixture only when the behavior cannot be represented programmatically, and explain why in the pull request.

## Pull requests

- Keep one behavioral change per pull request.
- Start from an issue that states the observable problem and acceptance criteria.
- Add a test that fails without the change.
- Update text output, JSON output, stdout/stderr routing, and the published schema when their contract changes.
- Preserve the documented exit codes.
- Do not weaken file, page, memory, or permission safeguards without explaining the operational impact.
- Do not add generated reports, local PDFs, IDE files, or debug output.

Use `Co-authored-by` trailers only when each named person made an actual contribution to the commit.

## Review checklist

Before requesting review, confirm:

- `mvn clean verify` passes;
- the action container builds when its packaging or integration code changes;
- the packaged JAR starts;
- new error paths return the intended exit code;
- claims in documentation match executable behavior;
- fixtures contain no sensitive information.
