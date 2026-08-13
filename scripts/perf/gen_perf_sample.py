#!/usr/bin/env python3
"""REL-05 性能基线 —— 生成性能测试夹具（纯 stdlib）。

产物（写入 --out 目录，默认 samples/perf/）：
  perf-10mb.epub   合法 ~10MB EPUB（首开门槛样本：恢复阅读 P95 ≤ 1.5s）
  perf-large.txt   ~50MB UTF-8 中文 TXT（内存非线性测试）

关键：撑体积用**随机像素 PNG**（不可压缩），否则 ZIP_DEFLATED 会把重复数据压回几 KB。
大图放在最后一章（恢复点在第 1 章 → 首开不解码大图，测的是真实首开成本）。
结构对齐 scripts/gen_typography_fixtures.py（mimetype STORED 首项）。
"""
from __future__ import annotations
import argparse, os, random, struct, zipfile, zlib

# 固定种子 → 每次生成字节相同的 EPUB → contentHash 去重生效（重复运行不堆书）。
# 像素仍为高熵随机数据（不可压缩），保证 EPUB 体积真实。
_RNG = random.Random(1337)


def _rand_bytes(n: int) -> bytes:
    if hasattr(_RNG, "randbytes"):      # Python 3.9+
        return _RNG.randbytes(n)
    return bytes(_RNG.getrandbits(8) for _ in range(n))  # 老版本兜底（慢）


def _png_bytes(width: int, target_bytes: int) -> bytes:
    """生成约 target_bytes 的合法 8-bit 灰度 PNG（随机像素，不可压缩）。"""
    row_len = 1 + width  # 每行：1 filter byte + width 像素
    rows = max(1, target_bytes // row_len)
    ihdr = struct.pack(">II5B", width, rows, 8, 0, 0, 0, 0)  # W x H grayscale
    raw = bytearray(_rand_bytes(rows * row_len))
    for r in range(rows):
        raw[r * row_len] = 0  # PNG filter = None
    co = zlib.compressobj(9)
    idat = co.compress(bytes(raw)) + co.flush()

    def chunk(typ: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + typ + data +
                struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) +
            chunk(b"IDAT", idat) + chunk(b"IEND", b""))


def _xhtml(title: str, body_paragraphs: int) -> bytes:
    paras = "\n".join(
        f"  <p>第 {i} 段。性能基线样本正文——爱丽丝梦游仙境节选：白兔先生掏出怀表，"
        f"匆匆跑过她的身旁。她好奇地跟了上去，钻进了一个深深的兔子洞。</p>"
        for i in range(body_paragraphs)
    )
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<html xmlns="http://www.w3.org/1999/xhtml">'
        '<head><meta charset="utf-8"/><title>' + title + '</title></head>'
        '<body><h1>' + title + '</h1>\n' + paras + '\n</body></html>'
    ).encode("utf-8")


def build_epub(path: str, target_mb: int = 10, title: str = "首开性能样本·10MB",
               ident: str = "perf-sample-10mb") -> None:
    target = target_mb * 1024 * 1024
    chapters = 12
    chapter_blobs = [_xhtml(f"第 {i+1} 章", 60) for i in range(chapters)]
    # 最后一章插入大图引用（恢复点在第 1 章 → 首开不解码大图）
    last = chapter_blobs[-1]
    chapter_blobs[-1] = last.replace(b"</body>", b'<p><img src="big.png" alt="bulk"/></p></body>')

    spine_xml = "\n".join(f'    <itemref idref="c{i}"/>' for i in range(chapters))
    manifest = "\n".join(
        f'    <item id="c{i}" href="ch{i}.xhtml" media-type="application/xhtml+xml"/>'
        for i in range(chapters)
    )
    container = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">'
        '<rootfiles><rootfile full-path="OEBPS/content.opf" '
        'media-type="application/oebps-package+xml"/></rootfiles></container>'
    )
    opf = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" '
        'unique-identifier="bookid" xml:lang="zh-CN">'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
        '<dc:identifier id="bookid">' + ident + '</dc:identifier>'
        '<dc:title>' + title + '</dc:title>'
        '<dc:language>zh-CN</dc:language><dc:creator>REL-05</dc:creator>'
        '<meta name="cover" content="cover-img"/>'
        '</metadata><manifest>\n'
        '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>'
        + manifest + '\n'
        '    <item id="cover-img" href="cover.png" media-type="image/png" properties="cover-image"/>'
        '    <item id="big" href="big.png" media-type="image/png"/>'
        '</manifest><spine>\n' + spine_xml + '\n</spine></package>'
    )
    nav = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<html xmlns="http://www.w3.org/1999/xhtml" '
        'xmlns:epub="http://www.idpf.org/2007/ops">'
        '<head><title>目录</title></head><body>'
        '<nav epub:type="toc"><ol>'
        + "\n".join(f'<li><a href="ch{i}.xhtml">第 {i+1} 章</a></li>' for i in range(chapters))
        + '</ol></nav></body></html>'
    ).encode("utf-8")

    cover = _png_bytes(128, 32 * 1024)                       # 小封面（书库缩略图便宜解码）
    text_total = sum(len(b) for b in chapter_blobs) + len(opf) + len(nav) + len(container)
    big_target = max(200_000, target - text_total - 40_000)  # 留 OPF/nav/cover/开销
    big = _png_bytes(512, big_target)

    # 固定 ZIP 时间戳 + 顺序 → 字节级确定性 → contentHash 去重跨运行生效。
    fixed_date = (2024, 1, 1, 0, 0, 0)

    def put(zf, name, data, compress=zipfile.ZIP_DEFLATED):
        zi = zipfile.ZipInfo(name, date_time=fixed_date)
        zi.compress_type = compress
        zf.writestr(zi, data)

    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        put(zf, "mimetype", "application/epub+zip", zipfile.ZIP_STORED)
        put(zf, "META-INF/container.xml", container)
        put(zf, "OEBPS/content.opf", opf)
        put(zf, "OEBPS/nav.xhtml", nav)
        put(zf, "OEBPS/cover.png", cover)
        put(zf, "OEBPS/big.png", big)
        for i, b in enumerate(chapter_blobs):
            put(zf, f"OEBPS/ch{i}.xhtml", b)


def build_txt(path: str, target_mb: int = 50) -> None:
    line = "性能基线大文件样本：床前明月光，疑是地上霜。举头望明月，低头思故乡。\n"
    line_b = line.encode("utf-8")
    count = target_mb * 1024 * 1024 // len(line_b) + 1
    with open(path, "wb") as f:
        f.write(line_b * count)


def main() -> None:
    ap = argparse.ArgumentParser(description="REL-05 性能夹具生成")
    ap.add_argument("--out", default="samples/perf", help="输出目录")
    ap.add_argument("--epub-mb", type=int, default=10)
    ap.add_argument("--txt-mb", type=int, default=50)
    ap.add_argument("--only", choices=["epub", "txt"])
    ap.add_argument("--title", default="首开性能样本·10MB")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    if args.only != "txt":
        p = os.path.join(args.out, "perf-10mb.epub")
        build_epub(p, args.epub_mb, title=args.title, ident="perf-" + args.title.encode("utf-8").hex()[:12])
        print(f"OK {p}  ({os.path.getsize(p)/1024/1024:.1f} MB)  title={args.title}")
    if args.only != "epub":
        p = os.path.join(args.out, "perf-large.txt")
        build_txt(p, args.txt_mb)
        print(f"OK {p}  ({os.path.getsize(p)/1024/1024:.1f} MB)")


if __name__ == "__main__":
    main()
