#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# 生成 TYPE-04 排版回归样本 EPUB（ruby 注音 / RTL / 竖排）。
#
# 纯 stdlib，产物结构对齐 app 自有 `TxtEpubConverter`（mimetype STORED 首项 + container/opf/nav/ncx/xhtml），
# 保证与生产 EPUB 同构、Readium 可正常解析。
#
# 产物落 `samples/public/typography/`（git 跟踪），供：
#   - JVM 结构校验：`TypographySamplesTest` 解压断言标记存在；
#   - 仪器层开书冒烟：`TypographySamplesOpenTest` 经 ReadiumFacade 打开；
#   - 真机肉眼回归：徐先生在 vivo 上打开核对（ruby 注音在上 / RTL 右起 / 竖排自上而下右起）。
#
# 内容均为公有领域 / 合成短文本（李白《静夜思》公有领域、通用阿拉伯语问候、自造拼音示例），无版权问题。
#
# 用法：`python3 scripts/gen_typography_fixtures.py`

import os
import zipfile

# dcterms:modified 固定常量（EPUB3 规范要求；固定值保证产物可复现、测试不抖动）。
MODIFIED = "2026-01-01T00:00:00Z"

# 产物目录：仓库根 / samples / public / typography
OUT_DIR = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "samples", "public", "typography")
)


def xml_escape(s):
    """XML 文本节点 / 属性值通用转义。"""
    return (
        s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
    )


def mimetype_entry(zf):
    """mimetype：必须是 ZIP 首项且 STORED（不压缩）。"""
    zi = zipfile.ZipInfo("mimetype")
    zi.compress_type = zipfile.ZIP_STORED
    zf.writestr(zi, "application/epub+zip")


def text_entry(zf, path, content):
    """写一个 DEFLATE 压缩的 UTF-8 文本条目。"""
    zi = zipfile.ZipInfo(path)
    zi.compress_type = zipfile.ZIP_DEFLATED
    zf.writestr(zi, content)


def container_xml():
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<container version="1.0" '
        'xmlns="urn:oasis:names:tc:opendocument:xmlns:container">'
        "<rootfiles>"
        '<rootfile full-path="OEBPS/content.opf" '
        'media-type="application/oebps-package+xml"/>'
        "</rootfiles></container>\n"
    )


def content_opf(
    *,
    title,
    identifier,
    language,
    chapters,
    spine_ppd=None,
    manifest_css=False,
):
    """OPF：metadata + manifest + spine。chapters=[(href, id), ...]。"""
    ppd = f' page-progression-direction="{spine_ppd}"' if spine_ppd else ""
    out = [
        '<?xml version="1.0" encoding="UTF-8"?>\n',
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" '
        'unique-identifier="pub-id">',
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">',
        f'<dc:identifier id="pub-id">{xml_escape(identifier)}</dc:identifier>',
        f"<dc:title>{xml_escape(title)}</dc:title>",
        f"<dc:language>{xml_escape(language)}</dc:language>",
        f'<meta property="dcterms:modified">{MODIFIED}</meta>',
        "</metadata>",
        "<manifest>",
        '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" '
        'properties="nav"/>',
        '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>',
    ]
    if manifest_css:
        out.append(
            '<item id="css" href="style.css" media-type="text/css"/>'
        )
    for href, cid in chapters:
        out.append(
            f'<item id="{cid}" href="{href}" '
            'media-type="application/xhtml+xml"/>'
        )
    out.append("</manifest>")
    out.append(f"<spine{ppd}>")
    for _, cid in chapters:
        out.append(f'<itemref idref="{cid}"/>')
    out.append("</spine>")
    out.append("</package>\n")
    return "".join(out)


def nav(title, chapters):
    """EPUB3 导航：nav epub:type=toc。chapters=[(href, label), ...]。"""
    out = [
        '<?xml version="1.0" encoding="UTF-8"?>\n',
        '<html xmlns="http://www.w3.org/1999/xhtml" '
        'xmlns:epub="http://www.idpf.org/2007/ops">',
        f"<head><title>{xml_escape(title)}</title></head>",
        "<body>",
        f'<nav epub:type="toc"><h1>{xml_escape(title)}</h1><ol>',
    ]
    for href, label in chapters:
        out.append(
            f'<li><a href="{href}">{xml_escape(label)}</a></li>'
        )
    out.append("</ol></nav>")
    out.append("</body></html>\n")
    return "".join(out)


def ncx(title, identifier, chapters):
    """EPUB2 导航兜底。chapters=[(href, label), ...]。"""
    out = [
        '<?xml version="1.0" encoding="UTF-8"?>\n',
        '<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">',
        f'<head><meta name="dtb:uid" content="{xml_escape(identifier)}"/></head>',
        f"<docTitle><text>{xml_escape(title)}</text></docTitle>",
        "<navMap>",
    ]
    for i, (href, label) in enumerate(chapters, start=1):
        out.append(
            f'<navPoint id="navpoint-{i}" playOrder="{i}">'
            f"<navLabel><text>{xml_escape(label)}</text></navLabel>"
            f'<content src="{href}"/>'
            "</navPoint>"
        )
    out.append("</navMap></ncx>\n")
    return "".join(out)


def chapter_xhtml(*, title, body_xhtml, html_attrs="", head_extra=""):
    """章节 XHTML。html_attrs 追加到 <html>（如 dir/lang）；head_extra 追加到 <head>（如 css link）。"""
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<html xmlns="http://www.w3.org/1999/xhtml"{html_attrs}>'
        f"<head><title>{xml_escape(title)}</title>{head_extra}</head>"
        f"<body><h1>{xml_escape(title)}</h1>{body_xhtml}</body></html>\n"
    )


def build_epub(
    path,
    *,
    title,
    identifier,
    language,
    chapters,
    spine_ppd=None,
    css=None,
):
    """组装一个 EPUB。

    chapters: list of dict {
        href, id, label, html_attrs, head_extra, body_xhtml
    }
    css: 可选样式表文本（写入 OEBPS/style.css，并在 OPF manifest 登记）。
    """
    opf_chapters = [(c["href"], c["id"]) for c in chapters]
    nav_chapters = [(c["href"], c["label"]) for c in chapters]
    with zipfile.ZipFile(path, "w") as zf:
        mimetype_entry(zf)
        text_entry(zf, "META-INF/container.xml", container_xml())
        text_entry(
            zf,
            "OEBPS/content.opf",
            content_opf(
                title=title,
                identifier=identifier,
                language=language,
                chapters=opf_chapters,
                spine_ppd=spine_ppd,
                manifest_css=css is not None,
            ),
        )
        text_entry(zf, "OEBPS/nav.xhtml", nav(title, nav_chapters))
        text_entry(zf, "OEBPS/toc.ncx", ncx(title, identifier, nav_chapters))
        if css is not None:
            text_entry(zf, "OEBPS/style.css", css)
        for c in chapters:
            text_entry(
                zf,
                f"OEBPS/{c['href']}",
                chapter_xhtml(
                    title=c["label"],
                    body_xhtml=c["body_xhtml"],
                    html_attrs=c.get("html_attrs", ""),
                    head_extra=c.get("head_extra", ""),
                ),
            )


# ===== 三个样本的内容定义（单一代码源）=====

# 1) ruby 注音：HTML5 <ruby>/<rt>，EPUB3 必需
RUBY_CHAPTERS = [
    {
        "href": "chapter-1.xhtml",
        "id": "chap-1",
        "label": "拼音注音",
        "html_attrs": ' lang="zh-CN" xml:lang="zh-CN"',
        "body_xhtml": (
            "<p>"
            '<ruby>汉<rt>hàn</rt></ruby>字 '
            '<ruby>注<rt>zhù</rt></ruby>音 '
            '<ruby>阅<rt>yuè</rt></ruby><ruby>读<rt>dú</rt></ruby>'
            "</p>"
            "<p>"
            '<ruby>中<rt>zhōng</rt></ruby>'
            '<ruby>文<rt>wén</rt></ruby>排版样本。'
            "</p>"
        ),
    }
]

# 2) RTL：阿拉伯语，dir=rtl + spine page-progression-direction=rtl
RTL_CHAPTERS = [
    {
        "href": "chapter-1.xhtml",
        "id": "chap-1",
        "label": "مرحبا",
        "html_attrs": ' dir="rtl" lang="ar" xml:lang="ar"',
        "body_xhtml": (
            "<p>مرحبا بالعالم</p>"
            "<p>هذا نموذج لاختبار الكتابة من اليمين إلى اليسار.</p>"
        ),
    }
]

# 3) 竖排：CSS writing-mode: vertical-rl，李白《静夜思》（公有领域）
VERTICAL_CSS = (
    "html { writing-mode: vertical-rl; -webkit-writing-mode: vertical-rl; "
    "line-height: 2; }"
)
VERTICAL_CHAPTERS = [
    {
        "href": "chapter-1.xhtml",
        "id": "chap-1",
        "label": "静夜思",
        "html_attrs": ' lang="zh-CN" xml:lang="zh-CN"',
        "head_extra": '<link rel="stylesheet" type="text/css" href="style.css"/>',
        "body_xhtml": (
            "<p>床前明月光，疑是地上霜。</p>"
            "<p>举头望明月，低头思故乡。</p>"
        ),
    }
]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    fixtures = [
        {
            "file": "ruby.epub",
            "title": "拼音注音样本",
            "identifier": "urn:uuid:typography-ruby",
            "language": "zh-CN",
            "chapters": RUBY_CHAPTERS,
        },
        {
            "file": "rtl.epub",
            "title": "RTL 排版样本",
            "identifier": "urn:uuid:typography-rtl",
            "language": "ar",
            "spine_ppd": "rtl",
            "chapters": RTL_CHAPTERS,
        },
        {
            "file": "vertical.epub",
            "title": "竖排样本",
            "identifier": "urn:uuid:typography-vertical",
            "language": "zh-CN",
            "chapters": VERTICAL_CHAPTERS,
            "css": VERTICAL_CSS,
        },
    ]
    for fx in fixtures:
        path = os.path.join(OUT_DIR, fx["file"])
        build_epub(
            path,
            title=fx["title"],
            identifier=fx["identifier"],
            language=fx["language"],
            chapters=fx["chapters"],
            spine_ppd=fx.get("spine_ppd"),
            css=fx.get("css"),
        )
        print(f"生成 {path}（{os.path.getsize(path)} bytes）")


if __name__ == "__main__":
    main()
