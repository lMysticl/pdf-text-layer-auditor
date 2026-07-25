# Changelog

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
