# Unicode and PDF font validation matrix

The auditor validates PDF text semantics by categories rather than by font file
names. There are hundreds of thousands of fonts and an effectively unbounded
number of Unicode strings, so “every font” cannot be proved by enumeration.

## Automated matrix

| Dimension | Covered cases |
| --- | --- |
| Writing systems | Latin (NFC and NFD), Greek, Cyrillic, Hebrew, Arabic, Devanagari, Bengali, Thai, Han, Hiragana, Katakana, Hangul |
| Symbols | Math, currency, dingbats, emoji, emoji ZWJ sequence, variation sequence |
| Direction and composition | Combining mark, right-to-left mark, multi-code-point ToUnicode destination, supplementary-plane characters |
| Font structures | Embedded horizontal Type 0/CIDFontType2 TrueType; embedded vertical `Identity-V` Type 0/CIDFontType0 CFF; Standard 14 non-embedded Type 1; Type 3 with/without ToUnicode; malformed/damaged Type 0 |
| Rejected mappings | NUL and C0 controls, U+FFFD, private-use, unassigned, BMP and supplementary-plane noncharacters |
| Observable proof | Unicode code-point counts, missing-mapping findings, embedded/damaged font state, extracted text, and non-blank rendered pixels |

Most PDF fixtures are generated in memory during the test run. The vertical
CJK/CFF case is a deterministic checked-in PDF generated from an OFL-1.1 Noto
Sans CJK subset; its source, hash, license and rebuild script are retained next
to the fixture. Tests never depend on fonts installed on the CI host.

## What this proves

- A valid ToUnicode mapping is accepted independently of script.
- Non-BMP and multi-code-point mappings are counted by Unicode code point, not
  UTF-16 code unit.
- Combining marks and direction/variation controls are preserved because they
  can be essential parts of valid text sequences.
- Values that cannot provide interoperable text semantics are routed to
  `MISSING_UNICODE`.
- A real embedded Unicode font produces extractable and visibly rendered Latin,
  Greek, and Cyrillic text.
- A Type 0 font without `/ToUnicode` is reported as implicit mapping even when
  PDFBox can infer a plausible string from its encoding.
- A real CIDFontType0 CFF program with `Identity-V` preserves exact Han,
  Hiragana, Katakana and Hangul extraction, reports vertical font/glyph
  evidence, and renders non-blank pixels.

## Boundary

The matrix does not certify typography, shaping, reading order, or visual
fidelity for every language. Those depend on the producing application, the
embedded font program, content-stream order, and layout engine. The auditor
diagnoses the native text layer; it does not replace OCR or a PDF/UA validator.
