#!/usr/bin/env python3
"""Rebuild the deterministic vertical CJK/CFF PDF test fixture."""

from __future__ import annotations

from pathlib import Path

from fontTools.ttLib import TTFont


ROOT = Path(__file__).resolve().parents[1]
FONT = ROOT / "src/test/resources/fonts/NotoSansCJKsc-Regular-subset.otf"
OUTPUT = ROOT / "src/test/resources/fonts/vertical-cjk-cff.pdf"
TEXT = "漢字あア한글"


def stream(dictionary: bytes, data: bytes) -> bytes:
    return (
        b"<< /Length "
        + str(len(data)).encode("ascii")
        + b" "
        + dictionary
        + b" >>\nstream\n"
        + data
        + b"\nendstream"
    )


def build() -> bytes:
    font = TTFont(FONT)
    cmap = font.getBestCmap()
    mappings: list[tuple[int, int]] = []
    for character in TEXT:
        code_point = ord(character)
        glyph_name = cmap[code_point]
        if not glyph_name.startswith("cid"):
            raise RuntimeError(f"Expected CID glyph name, got {glyph_name}")
        cid = int(glyph_name[3:])
        if not 0 <= cid <= 0xFFFF:
            raise RuntimeError(f"CID does not fit Identity-V code: {cid}")
        mappings.append((cid, code_point))
    cff = font["CFF "].compile(font)

    bfchars = "\n".join(
        f"<{cid:04X}> <{code_point:04X}>" for cid, code_point in mappings
    )
    to_unicode = f"""/CIDInit /ProcSet findresource begin
12 dict begin
begincmap
/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
/CMapName /Adobe-Identity-UCS def
/CMapType 2 def
1 begincodespacerange
<0000> <FFFF>
endcodespacerange
{len(mappings)} beginbfchar
{bfchars}
endbfchar
endcmap
CMapName currentdict /CMap defineresource pop
end
end
""".encode("ascii")
    encoded_text = "".join(f"{cid:04X}" for cid, _ in mappings)
    content = f"BT /F1 24 Tf 300 700 Td <{encoded_text}> Tj ET\n".encode("ascii")

    objects = {
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            b"/Resources << /Font << /F1 4 0 R >> >> /Contents 9 0 R >>"
        ),
        4: (
            b"<< /Type /Font /Subtype /Type0 /BaseFont /NotoSansCJKsc-Regular "
            b"/Encoding /Identity-V /DescendantFonts [5 0 R] /ToUnicode 8 0 R >>"
        ),
        5: (
            b"<< /Type /Font /Subtype /CIDFontType0 "
            b"/BaseFont /NotoSansCJKsc-Regular "
            b"/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> "
            b"/FontDescriptor 6 0 R /DW 1000 >>"
        ),
        6: (
            b"<< /Type /FontDescriptor /FontName /NotoSansCJKsc-Regular "
            b"/Flags 4 /FontBBox [-1002 -1048 2928 1808] /ItalicAngle 0 "
            b"/Ascent 1160 /Descent -288 /CapHeight 733 /StemV 80 "
            b"/FontFile3 7 0 R >>"
        ),
        7: stream(b"/Subtype /CIDFontType0C", cff),
        8: stream(b"", to_unicode),
        9: stream(b"", content),
    }

    pdf = bytearray(b"%PDF-1.7\n%\xE2\xE3\xCF\xD3\n")
    offsets = [0]
    for number in range(1, len(objects) + 1):
        offsets.append(len(pdf))
        pdf.extend(f"{number} 0 obj\n".encode("ascii"))
        pdf.extend(objects[number])
        pdf.extend(b"\nendobj\n")
    xref = len(pdf)
    pdf.extend(f"xref\n0 {len(objects) + 1}\n".encode("ascii"))
    pdf.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        pdf.extend(f"{offset:010d} 00000 n \n".encode("ascii"))
    pdf.extend(
        (
            f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
            f"startxref\n{xref}\n%%EOF\n"
        ).encode("ascii")
    )
    return bytes(pdf)


if __name__ == "__main__":
    OUTPUT.write_bytes(build())
    print(OUTPUT)
