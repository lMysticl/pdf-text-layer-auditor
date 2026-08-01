# Noto CJK test subset

`NotoSansCJKsc-Regular-subset.otf` is a test-only subset of
`Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf` from
[`notofonts/noto-cjk`](https://github.com/notofonts/noto-cjk) commit
`f8d157532fbfaeda587e826d4cd5b21a49186f7c`.

The subset retains only the glyphs needed to test Latin plus Han, Hiragana,
Katakana and Hangul vertical Type 0/CIDFontType0 CFF handling. It was produced
with fontTools 4.63.0, retaining glyph IDs, all layout features, vertical
metrics, names and the `.notdef` outline. Its SHA-256 is
`cf1d90acb57b06c919d3534b4e24f523c1f95eacc7f220ed23ead36467bf5b68`.

The original and this subset are distributed under the SIL Open Font License
1.1 in `OFL-NOTO-CJK.txt`.

`vertical-cjk-cff.pdf` is a deterministic, rights-clear PDF fixture generated
from this subset by `tools/generate_vertical_cff_fixture.py`. It embeds the CFF
program as a CIDFontType0 descendant, uses `Identity-V`, and maps the six test
characters through an explicit `/ToUnicode` CMap. Its SHA-256 is
`d5e5c9526693bc1fbcf60be65e244c5d79ac8960a6198a4a4c322e101448c84a`.
