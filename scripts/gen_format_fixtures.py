#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# 生成 V1 PDF/CBZ 格式回归样本（最小合法文件，纯合成内容无版权问题）。
#
# 纯 stdlib：
#   - minimal.pdf：手写 3 页文本型 PDF（xref 表 + 页树 + Helvetica 文本对象），
#     不依赖外部 PDF 库；结构校验断言 %PDF- magic / 页数 / 页文本。
#   - sample.cbz：stdlib zlib 造 1x1 PNG 打 ZIP（DEFLATE），Readium ImageParser 可解析。
#
# 产物落 `samples/public/formats/`（git 跟踪），供：
#   - JVM 结构校验：`FormatSamplesTest`；
#   - 真机回归：SAF 导入 → 打开翻页（PDF 连续/单页 + pinch 缩放；CBZ 翻页）。
#
# 用法：`python3 scripts/gen_format_fixtures.py`

import os
import struct
import zipfile
import zlib

OUT_DIR = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "samples", "public", "formats")
)

PAGE_LABELS = ["Page 1 / ebook PDF sample", "Page 2 / ebook PDF sample", "Page 3 / ebook PDF sample"]


def build_pdf() -> bytes:
    """手写最小 3 页 PDF。对象编号：1=Catalog 2=Pages 3..5=Page 6..8=Contents 9=Font。"""

    objects = {}

    def add(num, body: bytes):
        objects[num] = body

    # 页对象（3..5）与内容流（6..8）逐页生成。
    for i, label in enumerate(PAGE_LABELS):
        page_num = 3 + i
        content_num = 6 + i
        escaped = label.replace("(", r"\(").replace(")", r"\)")
        stream = (
            f"BT /F1 24 Tf 72 700 Td ({escaped}) Tj ET\n"
            f"BT /F1 12 Tf 72 660 Td (minimal pdf sample - ebook reader) Tj ET\n"
        ).encode("ascii")
        add(
            page_num,
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            b"/Resources << /Font << /F1 9 0 R >> >> /Contents "
            + f"{content_num} 0 R".encode()
            + b" >>",
        )
        add(
            content_num,
            b"<< /Length "
            + str(len(stream)).encode()
            + b" >>\nstream\n"
            + stream
            + b"endstream",
        )

    kids = b" ".join(f"{3 + i} 0 R".encode() for i in range(len(PAGE_LABELS)))
    add(1, b"<< /Type /Catalog /Pages 2 0 R >>")
    add(2, b"<< /Type /Pages /Kids [ " + kids + b" ] /Count " + str(len(PAGE_LABELS)).encode() + b" >>")
    add(9, b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    # 拼 header + 逐对象 + xref + trailer。
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num in sorted(objects):
        offsets[num] = len(out)
        out += f"{num} 0 obj\n".encode()
        out += objects[num]
        out += b"\nendobj\n"

    xref_pos = len(out)
    max_obj = max(objects)
    out += f"xref\n0 {max_obj + 1}\n".encode()
    out += b"0000000000 65535 f \n"
    for num in range(1, max_obj + 1):
        if num in offsets:
            out += f"{offsets[num]:010d} 00000 n \n".encode()
        else:
            out += b"0000000000 65535 f \n"
    out += (
        f"trailer\n<< /Size {max_obj + 1} /Root 1 0 R >>\nstartxref\n{xref_pos}\n%%EOF\n"
    ).encode()
    return bytes(out)


def make_png(width: int = 1, height: int = 1, rgb: tuple = (255, 0, 0)) -> bytes:
    """手写最小 PNG（单 IDAT，zlib DEFLATE 的原始像素行）。"""

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    # 像素行：filter byte 0 + RGB 三字节 × 宽。
    raw = b"".join(bytes((0, *rgb)) for _ in range(width)) * height
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)  # 8bit truecolor
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    pdf_path = os.path.join(OUT_DIR, "minimal.pdf")
    with open(pdf_path, "wb") as f:
        f.write(build_pdf())
    print(f"wrote {pdf_path} ({os.path.getsize(pdf_path)} bytes)")

    cbz_path = os.path.join(OUT_DIR, "sample.cbz")
    with zipfile.ZipFile(cbz_path, "w", zipfile.ZIP_DEFLATED) as zf:
        # 4 页：红/绿/蓝/白，文件名带序号（ImageParser 按名排序成 readingOrder）。
        for i, rgb in enumerate([(255, 0, 0), (0, 153, 0), (0, 68, 204), (255, 255, 255)], start=1):
            zf.writestr(f"page-{i:03d}.png", make_png(rgb=rgb))
    print(f"wrote {cbz_path} ({os.path.getsize(cbz_path)} bytes)")

    print("done.")


if __name__ == "__main__":
    main()
