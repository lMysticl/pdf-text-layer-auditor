# JSON report details

This page expands on the `--json` output summarized in the
[README](../README.md#quick-start). Field definitions are authoritative in the
[version 6 JSON Schema](report-schema-v6.json).

## Report contents

JSON output includes the report summary, parse health, evidence-completeness
flags, per-page text surfaces, raw-versus-semantic Unicode mapping, reading
order, page classification, image/vector/annotation inventory, glyph geometry
and paint-state observations, font state, and findings. Schema v3 records
display-oriented page geometry and bounded, text-free location boxes for
missing Unicode, replacement characters, tiny text, implicit composite
mapping, and RTL-profile findings. Schema v4 additionally records bounded
`IMAGE` regions so image-only and partial-OCR evidence can be located without
retaining image or document content. Schema v5 adds visible `ANNOTATION` and
`FORM_FIELD` regions plus exact counts for every supported object type. New
schema v6 adds crop- and clip-bounded `VECTOR_PATH` envelopes for visible fill,
stroke, and fill-and-stroke operations. It does not guess that a path is text.
New reports use the strict
[version 6 JSON Schema](report-schema-v6.json); the
[version 1](report-schema-v1.json) and
[version 2](report-schema-v2.json) and
[version 3](report-schema-v3.json) and
[version 4](report-schema-v4.json) and
[version 5](report-schema-v5.json) schemas remain published for existing
consumers. A `false` completeness flag means that the corresponding surface
was not assessed and must not be interpreted as clean. Successful reports
always set `extractionAllowed` to `true`; a PDF that forbids extraction
produces exit code `2` and no report. The default output remains human-readable.

## Spatial evidence

`spatialEvidence` uses PDF points with a top-left origin after page rotation.
It keeps at most eight boxes per locatable finding on each page, reports the
uncapped `totalLocationCount`, and sets `locationsTruncated` when more locations
exist. Boxes describe the extraction location, not an exact vector outline of
the painted glyph. No extracted text or font name is stored in a location.
Findings such as reading-order divergence or a page-wide OCR classification
remain page-level until a defensible smaller region can be derived.

## Visual regions

`visualRegions` records `IMAGE`, `ANNOTATION`, `FORM_FIELD`, and `VECTOR_PATH`
bounds. It keeps
at most 32 samples per object type on each page, reports exact per-type and
total counts, and sets `regionsTruncated` when any additional region is omitted.
Image regions are crop- and clip-intersected painted bounds; hidden optional
content and fully transparent image painting do not create a region. Annotation
and form field regions are view-visible, non-empty annotation/widget rectangles
intersected with the crop box; hidden, invisible, `NoView`, disabled optional
content, outside-crop, and rectangle-less objects are excluded. Vector paths
include visible fill and stroke envelopes after the current transformation,
crop, and clipping path. Hidden optional content, fully transparent,
all-zero-dash, fully clipped, and outside-crop paths are excluded. Shading
operations without a locatable path
remain in `paintedVectorPathCount` but do not create a region. A region is
location evidence, not a claim that every
pixel inside the rectangle contains text. Annotation contents, form values,
field names, extracted text, and image bytes are never retained in this
evidence.

## Script and font coverage

The auditor is script-neutral: it validates Unicode mappings rather than
assuming Latin text. Structured reports include observed scripts such as `HAN`,
`ARABIC`, `HEBREW`, `DEVANAGARI`, `THAI`, and `HANGUL`, together with RTL,
combining-mark, non-BMP, variation-selector, ZWJ, and bidi-control counts.
Font evidence includes subtype, encoding (`Identity-H`/`Identity-V` when
declared), vertical-writing mode, embedding/subsetting, `/ToUnicode` presence,
and raw unmapped glyph counts. A script appearing in this list proves that its
semantic Unicode was observed; it does not certify shaping or visual fidelity.
