# Changelog

## Unreleased

- Introduce report schema v2 with explicit parse health, evidence completeness,
  text-surface, semantic-mapping, reading-order, and geometry-assessment fields.
- Treat valid marked-content `/ActualText` as semantic text while preserving
  the underlying raw Unicode-mapping provenance.
- Convert malformed or empty declared `/ToUnicode` CMaps into stable typed
  diagnostics and page findings instead of relying on PDFBox log output.
- Detect character-sequence divergence between content-stream and
  position-sorted extraction without retaining extracted document text.
- Classify blank, vector-only, image-only, native-text, mixed, and sparse-OCR
  pages from text, path, and image evidence.
- Measure image coverage, painted vector paths, annotations, widgets, optional
  content, and glyph paint/alpha/crop/clip/overlap/rotation observations.
- Inspect every normal, rollover, and down annotation appearance stream for
  missing or replacement Unicode without merging appearance text into page text.
- Evaluate OCG and OCMD visibility, including visibility expressions, for View,
  Print, and Export destinations; malformed/cyclic expressions remain explicitly
  incomplete.
- Flag near-full-page combined image coverage with at most 32 extracted characters as a sparse
  OCR candidate while retaining visibility observations as non-failing evidence
  until corpus calibration proves safe decision thresholds.
- Keep report and GitHub Action schemas v1 published for existing consumers.

## 0.5.4 — 2026-08-01

- Validate ToUnicode destinations across representative writing systems,
  combining and directional characters, non-BMP symbols, and invalid Unicode
  categories instead of treating every non-control value as healthy text.
- Exercise readable embedded Type 0 text plus Type 1, Type 3, missing-map, and
  malformed-font paths with generated PDF fixtures.
- Enforce class, method, line, and branch coverage floors during `mvn verify`.

## 0.5.3 — 2026-07-31

- Continue auditing when a malformed Type 0 font is missing its required
  descendant font data instead of failing the entire PDF.
- Mark glyphs decoded through PDFBox's fallback font as missing Unicode so the
  affected page remains reviewable without being reported as healthy.
- Added a generated-PDF regression fixture for the malformed-font recovery.

## 0.5.2 — 2026-07-31

- Detect fallback text produced by the extractor when a font character code has
  no usable Unicode mapping, instead of reporting the text layer as healthy.
- Added a generated-PDF regression fixture for an unmapped Type 3 glyph.

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
