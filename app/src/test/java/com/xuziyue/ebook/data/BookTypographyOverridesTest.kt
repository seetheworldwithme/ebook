package com.xuziyue.ebook.data

import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TYPE-05 按书排版：partial override 序列化 + mergeTypography 合并语义单测（纯 JVM）。
 *
 * 验收锚点（design.md §4.4 TYPE-05）：
 * - 「可恢复全局默认」→ clear 语义（Repository 层删行）+ 合并层空覆盖 = 全局。
 * - 「无效字体不导致书籍无法打开」→ 解析层任何坏数据吞掉降级，不抛异常。
 */
class BookTypographyOverridesTest {

    // ===== 序列化往返 =====

    @Test
    fun `空覆盖序列化只含 schemaVersion 和 volumeKeyPaging`() {
        val json = BookTypographyOverrides.Empty.toJsonString()
        val back = BookTypographyOverrides.fromJsonString(json)
        // Empty.values 是全默认 ReaderTypography；volumeKeyPaging 默认 true 也落盘（非 nullable 布尔）。
        assertEquals(BookTypographyOverrides.Empty, back)
    }

    @Test
    fun `部分覆盖序列化往返只保留显式字段`() {
        val overrides = BookTypographyOverrides(
            values = ReaderTypography(
                fontSize = 1.3,
                fontFamily = "LXGW WenKai Screen",
                theme = ReaderTheme.DARK,
                volumeKeyPaging = false,
            ),
        )
        val back = BookTypographyOverrides.fromJsonString(overrides.toJsonString())
        assertEquals(1.3, back.values.fontSize!!, 1e-9)
        assertEquals("LXGW WenKai Screen", back.values.fontFamily)
        assertEquals(ReaderTheme.DARK, back.values.theme)
        // 未设置字段保持 null（partial override 语义）
        assertNull(back.values.lineHeight)
        assertNull(back.values.fontWeight)
        assertNull(back.values.textAlign)
        assertNull(back.values.scroll)
        assertEquals(false, back.values.volumeKeyPaging)
    }

    @Test
    fun `全部维度序列化往返`() {
        val overrides = BookTypographyOverrides(
            values = ReaderTypography(
                fontSize = 2.0,
                fontFamily = "serif",
                fontWeight = 1.5,
                lineHeight = 1.2,
                paragraphSpacing = 0.5,
                pageMargins = 2.0,
                textAlign = ReaderTextAlign.START,
                theme = ReaderTheme.SEPIA,
                scroll = ReaderScrollMode.SCROLL,
                volumeKeyPaging = false,
            ),
        )
        val back = BookTypographyOverrides.fromJsonString(overrides.toJsonString())
        assertEquals(overrides.values, back.values)
    }

    // ===== 坏数据降级（TYPE-05 验收：无效数据不挡书打开）=====

    @Test
    fun `坏 JSON 返回 Empty 不抛异常`() {
        assertEquals(BookTypographyOverrides.Empty, BookTypographyOverrides.fromJsonString("not-json{{{"))
        assertEquals(BookTypographyOverrides.Empty, BookTypographyOverrides.fromJsonString(null))
        assertEquals(BookTypographyOverrides.Empty, BookTypographyOverrides.fromJsonString(""))
    }

    @Test
    fun `枚举坏值降级 null 不崩`() {
        val json = """{"schemaVersion":1,"theme":"NO_SUCH_THEME","textAlign":"XX","scroll":"YY","fontSize":1.5}"""
        val back = BookTypographyOverrides.fromJsonString(json)
        assertNull(back.values.theme)
        assertNull(back.values.textAlign)
        assertNull(back.values.scroll)
        assertEquals(1.5, back.values.fontSize!!, 1e-9)
    }

    // ===== mergeTypography 合并语义 =====

    @Test
    fun `空覆盖时合并结果等于全局`() {
        val global = ReaderTypography(fontSize = 1.5, theme = ReaderTheme.DARK)
        val merged = mergeTypography(global, ReaderTypography())
        assertEquals(global, merged)
    }

    @Test
    fun `覆盖字段压全局未覆盖字段跟全局`() {
        val global = ReaderTypography(
            fontSize = 1.5,
            fontFamily = "serif",
            theme = ReaderTheme.DARK,
            lineHeight = 1.4,
        )
        val override = ReaderTypography(
            fontSize = 2.0, // 只覆盖字号
            theme = ReaderTheme.SEPIA, // 只覆盖主题
        )
        val merged = mergeTypography(global, override)
        assertEquals(2.0, merged.fontSize!!, 1e-9) // 覆盖生效
        assertEquals(ReaderTheme.SEPIA, merged.theme)
        assertEquals("serif", merged.fontFamily) // 未覆盖跟全局
        assertEquals(1.4, merged.lineHeight!!, 1e-9)
    }

    @Test
    fun `全局改动传导到未覆盖本书`() {
        // 本书只覆盖主题；全局字号从 1.5 改 1.8，本书字号跟着变（partial override 的核心价值）
        val override = ReaderTypography(theme = ReaderTheme.DARK)
        val before = mergeTypography(ReaderTypography(fontSize = 1.5), override)
        val after = mergeTypography(ReaderTypography(fontSize = 1.8), override)
        assertEquals(1.5, before.fontSize!!, 1e-9)
        assertEquals(1.8, after.fontSize!!, 1e-9)
    }

    @Test
    fun `enablePerBook 快照后全局已设字段不影响本书`() {
        // 开「仅本书生效」把当前全局快照落成覆盖 → 快照里非 null 的字段被钉住，全局再变不传导。
        // 注意：快照里为 null 的字段（从未显式设过）在序列化层不落盘，仍跟随全局——
        // 这是快照语义的已知边界（enablePerBookTypography 快照的是生效值，null 表示从未设置）。
        val snapshot = ReaderTypography(fontSize = 1.5, theme = ReaderTheme.DARK, volumeKeyPaging = true)
        val afterGlobalChange = ReaderTypography(fontSize = 3.0, theme = ReaderTheme.SEPIA, lineHeight = 2.0)
        val merged = mergeTypography(afterGlobalChange, snapshot)
        assertEquals(1.5, merged.fontSize!!, 1e-9) // 已钉住
        assertEquals(ReaderTheme.DARK, merged.theme) // 已钉住
        assertEquals(2.0, merged.lineHeight!!, 1e-9) // 快照未钉 → 跟全局
    }
}
