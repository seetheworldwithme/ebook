package com.xuziyue.ebook.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ReaderDisplaySettings] 引擎无关模型单测：默认值、不可变性、枚举稳定性。
 *
 * 与 [ReaderTypographyTest] 同范式（纯 JUnit，无 Android / Readium 依赖）。
 */
class ReaderDisplaySettingsTest {

    @Test
    fun `Default 全跟随系统（brightness null、keepScreenOn false、orientation null）`() {
        val d = ReaderDisplaySettings.Default

        assertNull(d.brightness) // null = 跟随系统亮度
        assertFalse(d.keepScreenOn) // false = 不常亮（产品默认）
        assertNull(d.orientation) // null = 跟随系统方向
    }

    @Test
    fun `copy 修改单字段后其余字段保持`() {
        val base = ReaderDisplaySettings.Default
        val tuned = base.copy(brightness = 0.8f, orientation = ReaderOrientation.LANDSCAPE)

        assertEquals(0.8f, tuned.brightness!!)
        assertEquals(ReaderOrientation.LANDSCAPE, tuned.orientation)
        // 未动字段保留
        assertFalse(tuned.keepScreenOn)
    }

    @Test
    fun `copy 设置 keepScreenOn 后其余字段保持`() {
        val base = ReaderDisplaySettings.Default
        assertFalse(base.keepScreenOn)

        val on = base.copy(keepScreenOn = true)
        assertEquals(true, on.keepScreenOn)
        // 未动字段保留
        assertNull(on.brightness)
        assertNull(on.orientation)
    }

    @Test
    fun `copy 设 brightness 为 null 恢复跟随系统`() {
        val base = ReaderDisplaySettings.Default.copy(brightness = 0.5f)
        assertEquals(0.5f, base.brightness!!)

        val restored = base.copy(brightness = null)
        assertNull(restored.brightness)
    }

    @Test
    fun `ReaderOrientation 的 name 稳定，可作持久化 key`() {
        // 持久化层用 enum.name 存取，name 不可随意改名（会破坏已落盘偏好）。
        assertEquals("SYSTEM", ReaderOrientation.SYSTEM.name)
        assertEquals("PORTRAIT", ReaderOrientation.PORTRAIT.name)
        assertEquals("LANDSCAPE", ReaderOrientation.LANDSCAPE.name)
    }

    @Test
    fun `ReaderOrientation valueOf 可从 name 还原`() {
        // 读回持久化值时用 valueOf(name)。
        assertEquals(ReaderOrientation.SYSTEM, ReaderOrientation.valueOf("SYSTEM"))
        assertEquals(ReaderOrientation.PORTRAIT, ReaderOrientation.valueOf("PORTRAIT"))
        assertEquals(ReaderOrientation.LANDSCAPE, ReaderOrientation.valueOf("LANDSCAPE"))
    }
}
