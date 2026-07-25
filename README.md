# PDF Text Layer Auditor

[![Build](https://github.com/lMysticl/pdf-text-layer-auditor/actions/workflows/build.yml/badge.svg)](https://github.com/lMysticl/pdf-text-layer-auditor/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)

A small Java CLI that reports basic native text-layer signals for each PDF page.

It is useful when a PDF looks correct but search, copy/paste, indexing, accessibility tooling, or downstream text extraction behaves unexpectedly.

## What it detects

- pages with no native text glyphs
- glyphs without a usable Unicode mapping
- Unicode replacement characters
- suspiciously tiny text below a configurable threshold (3 pt by default)
- fonts used on each page, including embedded and damaged status

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

JSON output includes the report summary, per-page metrics, font state, and findings. The default output remains human-readable.

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

Example:

```text
PDF Text Layer Audit
File: document.pdf
Pages: 2

Page 1: 154 glyphs, 154 Unicode characters, 2 fonts
  OK: no basic text-layer problems detected
Page 2: 0 glyphs, 0 Unicode characters, 0 fonts
  WARN NO_TEXT_LAYER: No native text glyphs were found; the page may be blank or image-only.

Result: 1 of 2 pages need attention
```

## Exit codes

| Code | Meaning |
|---:|---|
| `0` | No basic text-layer problems detected |
| `1` | One or more pages need attention |
| `2` | Invalid arguments or the PDF could not be audited |

This makes the tool usable in CI without parsing its human-readable output.

## Input limits

The default audit rejects files larger than 100 MiB and documents with more than 1,000 pages. Temporary PDF streams are buffered on disk instead of an unrestricted heap cache.

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
- A healthy native text layer does not guarantee correct reading order.
- The audit does not determine whether text is visible, on-page, or unclipped.
- PDF text coordinates and Unicode mappings depend on the source file.
- Annotation appearance text is not included.
- Password-protected PDFs are not supported in the first release.
- Results are diagnostics, not PDF/UA or accessibility certification.

## Development

```bash
mvn verify
```

The tests create small synthetic PDFs at runtime, so the repository does not need large binary fixtures.

## License

Licensed under the Apache License, Version 2.0.
