# PDF Text Layer Auditor

[![Build](https://github.com/lMysticl/pdf-text-layer-auditor/actions/workflows/build.yml/badge.svg)](https://github.com/lMysticl/pdf-text-layer-auditor/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![GitHub Marketplace](https://img.shields.io/badge/Marketplace-PDF_Text_Layer_Audit-blue?logo=github)](https://github.com/marketplace/actions/pdf-text-layer-audit)

PDF Text Layer Auditor is a Java CLI for diagnosing missing or suspicious native
text layers before they disrupt search, copy/paste, indexing, accessibility
workflows, or downstream text extraction.

A PDF can look correct in a viewer while containing no searchable text, broken
Unicode mappings, replacement characters, or text that is too small to be
useful. The auditor inspects these signals page by page and produces either a
readable terminal report or versioned JSON for automated checks.

Use it to investigate a troublesome PDF, preflight documents before ingestion,
or fail a CI job when a document needs attention. It does not run OCR, modify
the source file, or claim accessibility conformance.

## What it detects

- pages with no native text glyphs
- glyphs without a usable Unicode mapping
- Unicode replacement characters
- suspiciously tiny text below a configurable threshold (3 pt by default)
- fonts used on each page, including embedded and damaged status

## GitHub Action

The repository also provides a Docker-based GitHub Action that audits every
added, modified, or renamed PDF in a pull request. It reads the changed-file
list through the GitHub REST API, adds file annotations to the workflow check,
writes a job summary, and creates one combined JSON report.

Install it from the
[GitHub Marketplace](https://github.com/marketplace/actions/pdf-text-layer-audit).

```yaml
name: PDF text layer

on:
  pull_request:

permissions:
  contents: read
  pull-requests: read

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - name: Check out the pull request
        uses: actions/checkout@v7
        with:
          lfs: true

      - name: Audit changed PDFs
        id: pdf-audit
        uses: lMysticl/pdf-text-layer-auditor@v0.5.4
        with:
          token: ${{ github.token }}

      - name: Upload the JSON report
        if: always() && steps.pdf-audit.outputs.report_path != ''
        uses: actions/upload-artifact@v7
        with:
          name: pdf-text-layer-audit
          path: ${{ steps.pdf-audit.outputs.report_path }}
```

The action intentionally supports `pull_request` only. It rejects
`pull_request_target`, where checking out and parsing untrusted pull-request
content can expose a more privileged token. The documented workflow grants
read-only access; annotations are emitted through GitHub workflow commands and
do not require `checks: write`.

Docker actions run on Linux runners. When a repository stores PDFs in Git LFS,
configure the checkout step with `lfs: true`; otherwise the action receives the
small LFS pointer file instead of the PDF.

Inputs:

| Input | Default | Meaning |
|---|---:|---|
| `token` | required | Token used only to list pull-request files |
| `fail_on_findings` | `true` | Fail when at least one page needs attention |
| `max_annotations` | `20` | Maximum annotations emitted by the step |
| `max_files` | `50` | Maximum changed PDFs audited in one pull request |
| `max_total_size_mib` | `500` | Maximum combined size of changed PDFs |
| `max_file_size_mib` | `100` | Per-file input-size limit |
| `max_pages` | `1000` | Per-file page-count limit |
| `tiny_text_threshold_pt` | `3` | Tiny-text threshold; `0` disables it |
| `report_path` | `pdf-text-layer-audit.json` | Workspace-relative JSON report path |

Outputs:

| Output | Meaning |
|---|---|
| `files_checked` | PDFs audited successfully |
| `files_with_findings` | Audited PDFs containing findings |
| `files_failed` | PDFs that could not be audited |
| `report_path` | Workspace-relative path to the combined JSON report |

Deleted PDFs and non-PDF files are ignored. Before parsing begins, the action
rejects a workload above `max_files` or `max_total_size_mib`; both limits can be
raised explicitly for a controlled repository. GitHub exposes at most 3,000
files through the pull-request files endpoint, so the action also rejects
larger pull requests instead of silently auditing an incomplete list.

The combined report follows the versioned
[GitHub Action report schema v2](docs/action-report-schema-v2.json). Each
successful file entry embeds the
[auditor report schema v2](docs/report-schema-v2.json). The v1 schemas remain
available for consumers of releases through 0.5.x.

## Quick start

Download the prebuilt executable JAR from the [latest release](https://github.com/lMysticl/pdf-text-layer-auditor/releases/latest), or build it locally.

Requirements:

- Java 21 or newer
- Maven 3.9 or newer

Build the executable JAR:

```bash
mvn clean package
```

Audit a PDF:

```bash
java -jar target/pdf-text-layer-auditor.jar document.pdf
```

Emit a machine-readable report:

```bash
java -jar target/pdf-text-layer-auditor.jar --json document.pdf
```

JSON output includes the report summary, parse health, evidence-completeness
flags, per-page text surfaces, raw-versus-semantic Unicode mapping, reading
order, page classification, image/vector/annotation inventory, glyph geometry
and paint-state observations, font state, and findings. New reports use the strict
[version 2 JSON Schema](docs/report-schema-v2.json); the
[version 1 schema](docs/report-schema-v1.json) remains published for existing
consumers. A `false` completeness flag means that the corresponding surface
was not assessed and must not be interpreted as clean. Successful reports
always set `extractionAllowed` to `true`; a PDF that forbids extraction
produces exit code `2` and no report. The default output remains human-readable.

The tiny-text threshold defaults to 3 pt. Adjust it for a specific workflow, or use `0` to disable that finding:

```bash
java -jar target/pdf-text-layer-auditor.jar \
  --tiny-text-threshold-pt 2.5 \
  document.pdf
```

Check the installed build:

```bash
java -jar target/pdf-text-layer-auditor.jar --version
```

Inspect only selected pages:

```bash
java -jar target/pdf-text-layer-auditor.jar --pages 1,3-5 document.pdf
```

The report keeps the document page numbers and distinguishes total pages from inspected pages.

The auditor is script-neutral: it validates Unicode mappings rather than
assuming Latin text. Schema v2 reports observed scripts such as `HAN`,
`ARABIC`, `HEBREW`, `DEVANAGARI`, `THAI`, and `HANGUL`, together with RTL,
combining-mark, non-BMP, variation-selector, ZWJ, and bidi-control counts.
Font evidence includes subtype, encoding (`Identity-H`/`Identity-V` when
declared), vertical-writing mode, embedding/subsetting, `/ToUnicode` presence,
and raw unmapped glyph counts. A script appearing in this list proves that its
semantic Unicode was observed; it does not certify shaping or visual fidelity.

Example:

```text
PDF Text Layer Audit
File: /path/to/document.pdf
Size: 0.02 MiB
Pages in document: 2
Pages inspected: 2
Encrypted: false
Tiny text threshold: 3.00 pt

Page 1: 154 glyphs, 154 Unicode characters, 2 fonts
  Font: Helvetica | embedded=false | damaged=false | glyphs=120
  Font: Times-Roman | embedded=false | damaged=false | glyphs=34
  OK: no basic text-layer problems detected
Page 2: 0 glyphs, 0 Unicode characters, 0 fonts
  WARN NO_TEXT_LAYER: No native text glyphs were found; the page may be blank or image-only.

Result: 1 of 2 pages need attention
```

## Output streams

Reports, help, and version information are written to standard output. Invalid arguments, audit failures, and output failures are written to standard error. In JSON mode, standard output contains only the JSON document.

Keep the streams separate when saving a report:

```bash
java -jar target/pdf-text-layer-auditor.jar --json document.pdf \
  > report.json 2> audit-error.log
```

Exit code `1` still means that a valid report was written; one or more inspected pages need attention.

## Exit codes

| Code | Meaning |
|---:|---|
| `0` | No basic text-layer problems detected |
| `1` | One or more pages need attention |
| `2` | Invalid arguments, audit failure, or output failure |

This makes the tool usable in CI without parsing its human-readable output.

## Input limits

The default audit rejects files larger than 100 MiB and documents with more than
1,000 pages. The GitHub Action additionally limits one pull request to 50 PDFs
and 500 MiB of PDF input in total. Temporary PDF streams are buffered on disk
instead of an unrestricted heap cache.

Controlled environments can override either limit:

```bash
java -jar target/pdf-text-layer-auditor.jar \
  --max-file-size-mib 250 \
  --max-pages 2500 \
  document.pdf
```

These limits reduce accidental resource use but do not sandbox the PDF parser. Process untrusted files in an isolated environment with separate CPU, memory, disk, and time limits.

## Honest limitations

- This tool does not perform OCR.
- The `SPARSE_OCR` heuristic means the union of painted images covers at least
  75% of the page and the page exposes at most 32 Unicode characters; it is a review signal, not a
  claim that OCR is wrong.
- Paint mode, alpha, crop/clip origin, overlap, rotation, and vertical-font
  counters are observations. They are not automatic failures because invisible
  OCR and `/ActualText` layers can be legitimate.
- Geometry uses glyph origins rather than complete rendered glyph outlines, so
  it does not prove full or partial visual visibility.
- PDF text coordinates and Unicode mappings depend on the source file.
- Normal, rollover, and down annotation appearance streams are checked
  separately from page text. Form values without a generated appearance stream
  cannot be inferred from the field value alone.
- OCG/OCMD visibility is evaluated for View, Print, and Export. A malformed,
  cyclic, or unsupported visibility expression makes `optionalContent=false`
  in the document completeness object instead of being guessed clean.
- Password-protected PDFs are not supported.
- Results are diagnostics, not PDF/UA or accessibility certification.

## Development

```bash
mvn verify
```

Build the same container used by the GitHub Action:

```bash
docker build -t pdf-text-layer-audit-action .
```

The tests create small synthetic PDFs at runtime, so the repository does not need large binary fixtures.
They include a deterministic [Unicode and PDF font validation matrix](docs/unicode-font-validation.md)
covering representative scripts, combining and directional characters, non-BMP symbols,
embedded Type 0 fonts, Type 1 and Type 3 fonts, and malformed mappings. The matrix is
category-based: no finite test suite can enumerate every font file or every Unicode string.

`mvn verify` enforces code-coverage floors and writes the HTML report to
`target/site/jacoco/index.html`.

`mvn verify` also creates a CycloneDX 1.6 software bill of materials at `target/bom.json`. Tagged releases publish the SBOM beside the executable JAR and checksum. Release artifacts include signed build-provenance attestations that can be verified with:

```bash
gh attestation verify pdf-text-layer-auditor.jar --repo lMysticl/pdf-text-layer-auditor
```

See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a fixture, issue, or pull request. Report suspected vulnerabilities through the private process in [SECURITY.md](SECURITY.md).

## License

Licensed under the Apache License, Version 2.0.
