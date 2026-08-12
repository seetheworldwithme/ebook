package com.xuziyue.ebook.model

/**
 * 引擎无关的屏幕方向设置（design.md §4.4 TYPE-03）。
 *
 * - [SYSTEM]：跟随系统方向（不锁定，用户旋转设备时自动切换）。
 * - [PORTRAIT]：强制竖屏。
 * - [LANDSCAPE]：强制横屏。
 *
 * name 用作持久化 key，不可随意改名（会破坏已落盘偏好）。
 */
enum class ReaderOrientation { SYSTEM, PORTRAIT, LANDSCAPE }

/**
 * 引擎无关的阅读显示/环境设置（design.md §4.4 TYPE-03）。
 *
 * 与 [ReaderTypography]（纯排版）分离：这三项都是 **Window / Activity 层副作用**，
 * 不传给 Readium 引擎（类似 [ReaderTypography.volumeKeyPaging] 是 app 层开关不映射引擎）。
 *
 * 全局偏好（跨书共享）；按书保存是 P1 的 TYPE-05，本类型不含 bookId。
 * 字段语义：
 * - [brightness]：null = 跟随系统亮度（Window `screenBrightness = -1f`）；
 *   0.0–1.0 = 手动亮度（仅影响 app 窗口，不改系统设置）。
 * - [keepScreenOn]：true = 阅读时保持屏幕常亮（Window `FLAG_KEEP_SCREEN_ON`）。
 *   默认 false（不强制常亮），退出阅读器自动清除。
 * - [orientation]：null / [ReaderOrientation.SYSTEM] = 跟随系统方向；
 *   [ReaderOrientation.PORTRAIT] / [ReaderOrientation.LANDSCAPE] = 锁定方向。
 *   退出阅读器恢复 [android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED]。
 *
 * 作用域：仅在阅读器内生效（进入应用 reader 屏幕 apply，退出 restore），
 * 不影响书库 / 详情页的方向与亮度（它们始终走系统默认）。
 */
data class ReaderDisplaySettings(
    val brightness: Float? = null,
    val keepScreenOn: Boolean = false,
    val orientation: ReaderOrientation? = null,
) {
    companion object {
        /**
         * 首次默认：全跟随系统（亮度系统默认、不常亮、方向跟随系统）。
         *
         * Repository 在 DataStore 无记录时返回此值；用户改动后各项被显式覆盖并持久化。
         */
        val Default: ReaderDisplaySettings = ReaderDisplaySettings()
    }
}
