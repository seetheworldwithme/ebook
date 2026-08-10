package com.xuziyue.ebook.reader.readium.txt

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把 [TxtBook] 打包成标准 EPUB 3.0 的 ZIP（P0V-04 方案 A：复用 Readium 已验证的 EPUB 全链路）。
 *
 * 纯 Kotlin（`java.util.zip`），不引第三方 EPUB 库；零 Android / Readium 依赖，可独立单测
 * （与 [TxtParser] 同包同风格）。
 *
 * ## 产物结构（Readium [EpubParser][org.readium.r2.streamer.parser.epub.EpubParser] 最小必需集）
 *
 *   ```
 *   mimetype                    (STORED 首项，内容 application/epub+zip)
 *   META-INF/container.xml      (rootfile → OEBPS/content.opf)
 *   OEBPS/content.opf           (metadata + manifest + spine)
 *   OEBPS/nav.xhtml             (EPUB3 导航，properties=nav)
 *   OEBPS/toc.ncx               (EPUB2 导航兜底)
 *   OEBPS/chapter-{i}.xhtml     (每章一个)
 *   ```
 *
 * 硬性约束（已逐文件核对 Readium 解析源码，详见 P0V-04 计划）：
 * - mimetype 必须是 ZIP 第一项且 STORED（`ZipOutputStream` 对 STORED 不自动算 size/crc，需手动设）。
 * - container.xml 的 rootfile 在 OPC 命名空间；OPF 的 metadata/manifest/spine 在 OPF 命名空间。
 * - nav.xhtml 的 `<nav>` 需 `epub:type="toc"`（OPS 命名空间）。
 * - 章节 XHTML 需 XML 声明 + XHTML 默认命名空间。
 *
 * @param book 已解析的 TXT 全书（chapters 非空）。
 * @param title 书名（写入 dc:title / nav / ncx / 各章 head）。
 * @param identifier 稳定唯一标识（写入 dc:identifier；调用方传原 txt 的 SHA-256，便于缓存复用）。
 */
class TxtEpubConverter {

    /** 转换并返回完整 EPUB 字节数组。 */
    fun convert(book: TxtBook, title: String, identifier: String): ByteArray {
        val out = ByteArrayOutputStream()
        writeTo(book, title, identifier, out)
        return out.toByteArray()
    }

    /** 转换并写入目标流（直接落盘缓存，省一次内存拷贝）。 */
    fun writeTo(book: TxtBook, title: String, identifier: String, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            writeMimetype(zip)
            writeEntry(zip, "META-INF/container.xml", containerXml())
            writeEntry(zip, "OEBPS/content.opf", contentOpf(book, title, identifier))
            writeEntry(zip, "OEBPS/nav.xhtml", nav(book, title))
            writeEntry(zip, "OEBPS/toc.ncx", ncx(book, title, identifier))
            book.chapters.forEach { ch ->
                writeEntry(zip, "OEBPS/chapter-${ch.index}.xhtml", chapter(ch))
            }
        }
    }

    // ===== 各 EPUB 文件内容 =====

    /**
     * mimetype：必须是 ZIP 首项且 STORED（不压缩）。
     *
     * STORED 模式下 [ZipOutputStream] 不自动计算 size/crc，必须在 [putNextEntry] 前手动设全，
     * 否则写出损坏 ZIP。
     */
    private fun writeMimetype(zip: ZipOutputStream) {
        val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(bytes) }.value
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            setSize(bytes.size.toLong())
            setCompressedSize(bytes.size.toLong())
            setCrc(crc)
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    /** 写一个 DEFLATE 压缩的 UTF-8 文本条目。 */
    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    /** META-INF/container.xml：OPC 命名空间，指向 OPF。 */
    private fun containerXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
            "<rootfiles>" +
            "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>" +
            "</rootfiles></container>\n"

    /** OEBPS/content.opf：OPF 命名空间，metadata + manifest + spine。 */
    private fun contentOpf(book: TxtBook, title: String, identifier: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"pub-id\">")
        append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
        append("<dc:identifier id=\"pub-id\">").append(xmlEscape(identifier)).append("</dc:identifier>")
        append("<dc:title>").append(xmlEscape(title)).append("</dc:title>")
        append("<dc:language>zh-CN</dc:language>")
        append("<meta property=\"dcterms:modified\">").append(MODIFIED).append("</meta>")
        append("</metadata>")
        append("<manifest>")
        append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
        append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>")
        book.chapters.forEach { ch ->
            append("<item id=\"chap-").append(ch.index)
            append("\" href=\"chapter-").append(ch.index)
            append(".xhtml\" media-type=\"application/xhtml+xml\"/>")
        }
        append("</manifest>")
        append("<spine>")
        book.chapters.forEach { ch ->
            append("<itemref idref=\"chap-").append(ch.index).append("\"/>")
        }
        append("</spine>")
        append("</package>\n")
    }

    /** OEBPS/nav.xhtml：EPUB3 导航（XHTML ns + OPS ns，nav 需 epub:type=toc）。 */
    private fun nav(book: TxtBook, title: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">")
        append("<head><title>").append(xmlEscape(title)).append("</title></head>")
        append("<body>")
        append("<nav epub:type=\"toc\"><h1>").append(xmlEscape(title)).append("</h1><ol>")
        book.chapters.forEach { ch ->
            append("<li><a href=\"chapter-").append(ch.index)
            append(".xhtml\">").append(xmlEscape(ch.title)).append("</a></li>")
        }
        append("</ol></nav>")
        append("</body></html>\n")
    }

    /** OEBPS/toc.ncx：EPUB2 导航兜底（NCX 命名空间；Readium 优先用 nav，此为保险）。 */
    private fun ncx(book: TxtBook, title: String, identifier: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">")
        append("<head><meta name=\"dtb:uid\" content=\"").append(xmlEscape(identifier)).append("\"/></head>")
        append("<docTitle><text>").append(xmlEscape(title)).append("</text></docTitle>")
        append("<navMap>")
        book.chapters.forEachIndexed { i, ch ->
            append("<navPoint id=\"navpoint-").append(ch.index)
            append("\" playOrder=\"").append(i + 1).append("\">")
            append("<navLabel><text>").append(xmlEscape(ch.title)).append("</text></navLabel>")
            append("<content src=\"chapter-").append(ch.index).append(".xhtml\"/>")
            append("</navPoint>")
        }
        append("</navMap></ncx>\n")
    }

    /** OEBPS/chapter-{i}.xhtml：每章正文（XHTML ns；按行段落化，非空行包 `<p>`）。 */
    private fun chapter(ch: TxtChapter): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<html xmlns=\"http://www.w3.org/1999/xhtml\">")
        append("<head><title>").append(xmlEscape(ch.title)).append("</title></head>")
        append("<body>")
        append("<h1>").append(xmlEscape(ch.title)).append("</h1>")
        // 按 \n 切行：非空 trim 行 → <p>；空行跳过（避免大量空 <p>）。标题重复在 <h1> 作章节头。
        ch.body.split('\n').forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                append("<p>").append(xmlEscape(trimmed)).append("</p>")
            }
        }
        append("</body></html>\n")
    }

    /** XML 文本转义（文本节点与属性值通用）。 */
    private fun xmlEscape(s: String): String = buildString {
        for (c in s) when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private companion object {
        /** dcterms:modified 固定常量（EPUB3 规范要求；固定值保证测试可复现、不依赖时间）。 */
        const val MODIFIED = "2026-01-01T00:00:00Z"
    }
}
