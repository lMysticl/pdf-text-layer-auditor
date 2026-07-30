# Changelog

## Unreleased

## 0.5.1 — 2026-07-30

- Treat glyph mappings containing ISO control characters as unusable Unicode
  instead of accepting them as a healthy text layer.
- Added a generated-PDF regression fixture for control-only Unicode mappings.

## 0.5.0 — 2026-07-28

- Bounded GitHub Action workloads by both changed-PDF count and combined input
  size before parsing begins.
- Added configurable `max_files` and `max_total_size_mib` Action inputs.

## 0.4.1 — 2026-07-28

- Updated Jackson Databind to 2.22.1 and refreshed test-only schema/logging dependencies.
- Added a deterministic end-to-end Action test with a synthetic PDF and mock
  pull-request files API.
- Kept Docker build and runtime images on the documented Java 21 baseline.
- Added direct GitHub Marketplace links to the project documentation.

## 0.4.0 — 2026-07-27

- Added a Docker-based GitHub Action for auditing PDFs changed by pull requests.
- Read pull-request file lists through the GitHub REST API with explicit
  pagination and completeness checks.
- Added workflow annotations, job summaries, bounded annotation counts,
  versioned combined JSON reports, and reusable action outputs.
- Restricted the action to `pull_request` and documented read-only token
  permissions for untrusted PDF input.
- Added action contract tests and an end-to-end pull-request smoke job.
- Documented and tested the stdout/stderr contract for human-readable and JSON output.
- Return exit code 2 when command output cannot be written.
- Documented that the CLI emits reports only when PDF permissions allow text extraction.
- Use the singular `font` label when a page contains exactly one font.
- Use singular grammar in one-page attention summaries.

## 0.3.1 — 2026-07-25

- Escaped terminal control characters in file paths, font names, and error messages.
- Pinned GitHub Actions to immutable revisions, restricted release tags to `main`, and added signed build provenance.
- Added automated validation of JSON reports against the published schema.
- Added regression coverage for encrypted, password-protected, and extraction-restricted PDFs.
- Updated JUnit and the Maven compiler, test, and packaging plugins.

## 0.3.0 — 2026-07-25

- Added strict CLI parsing and an installed-version command.
- Made file-size, page-count, and tiny-text limits configurable.
- Added non-contiguous page selection while preserving document page numbers.
- Published a strict version 1 schema for JSON reports.
- Added contribution, security, issue, and pull request guidance.
- Added Dependabot updates, CodeQL analysis, verified release automation, checksums, and a CycloneDX 1.6 SBOM.
- Preserved Apache license and notice metadata in the executable JAR.

## 0.2.0 — 2026-07-25

- Added machine-readable JSON output with `--json`.
- Included report summaries, per-page metrics, font state, and findings in JSON.
- Added JSON escaping and command-line integration tests.
- Updated GitHub Actions to the current Node 24-based releases.

## 0.1.0 — 2026-07-25

- Initial command-line auditor for native PDF text-layer signals.
- Added page-level findings for missing text, missing Unicode mappings, replacement characters, and suspiciously tiny text.
- Added font embedding and damage diagnostics.
- Added bounded file/page processing and disk-backed PDF stream caching.
