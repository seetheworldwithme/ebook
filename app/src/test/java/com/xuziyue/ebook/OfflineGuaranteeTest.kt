package com.xuziyue.ebook

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SET-04 离线 / 无网络验证（红线 #8：核心阅读必须在无网络权限下也能完成）。
 *
 * 把「不上传 / 无网络」这一事实**固化成防回退测试**：将来谁加网络权限 / 网络 SDK / 直接网络调用，
 * 对应断言即红，强制走人工评审（改本测试白名单或回退改动）。
 *
 * 现状（2026-08）：源码 Manifest 零 `<uses-permission>`；合并后的发布 Manifest 仅有依赖带来的
 * `ACCESS_NETWORK_STATE` / `WAKE_LOCK`（均为查询/保活类，**不含 INTERNET**），故 App 无法打开任何网络套接字；
 * 核心阅读流（开书 / 翻页 / 书签 / 批注 / 书内搜索 / 导入）全部走本地文件，零网络调用。
 */
class OfflineGuaranteeTest {

    /** 1. 源码 Manifest 不得声明任何网络权限（开发者直控部分）。 */
    @Test
    fun sourceManifests_declareNoNetworkPermissions() {
        val manifests = listOf(
            "app/src/main/AndroidManifest.xml",
            "reader/readium/src/main/AndroidManifest.xml",
            "core/model/src/main/AndroidManifest.xml",
        ).map { File(repoRoot(), it) }
        manifests.forEach { m ->
            assertTrue("源码 Manifest 应存在：${m.path}", m.exists())
            val text = m.readText()
            BANNED_PERMISSIONS.forEach { perm ->
                assertFalse(
                    "源码 Manifest ${m.path} 不应声明 $perm（红线 #8）",
                    text.contains(perm),
                )
            }
        }
    }

    /**
     * 2. 合并后的发布 Manifest 不得含 INTERNET（这是「能联网」的充要权限）。
     *
     * 扫描 AGP 合并产物（testDebugUnitTest 依赖 processDebugManifest，必有），排除 UnitTest 变体。
     * ACCESS_NETWORK_STATE / WAKE_LOCK 等查询类权限可能由依赖带入，**放行**——它们不能打开套接字。
     */
    @Test
    fun shippedMergedManifest_hasNoInternetPermission() {
        val merged = mergedAppManifests()
        assertTrue(
            "应至少有一个合并后的 App Manifest（AGP 产物路径变了吗？）：app/build/intermediates/merged_manifest*",
            merged.isNotEmpty(),
        )
        merged.forEach { m ->
            assertFalse(
                "合并后的发布 Manifest 不应含 INTERNET 权限（红线 #8，否则即可联网）：${m.path}",
                m.readText().contains("android.permission.INTERNET"),
            )
        }
    }

    /** 3. version catalog 不得引入网络 / 分析 / 广告 SDK。 */
    @Test
    fun versionCatalog_hasNoNetworkOrAnalyticsDeps() {
        val toml = File(repoRoot(), "gradle/libs.versions.toml")
        assertTrue("version catalog 应存在", toml.exists())
        val text = toml.readText()
        BANNED_LIBS.forEach { lib ->
            assertFalse(
                "version catalog 不应引入网络/分析库 '$lib'（红线 #8）",
                text.contains(lib, ignoreCase = true),
            )
        }
    }

    /** 4. 业务源码不得直接发起网络调用（库前缀 / 平台网络原语）。 */
    @Test
    fun source_hasNoDirectNetworkCalls() {
        val sourceRoots = listOf(
            "app/src/main",
            "reader/readium/src/main",
            "core/model/src/main",
        ).map { File(repoRoot(), it) }.filter { it.exists() }

        val violations = mutableListOf<String>()
        sourceRoots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { f ->
                    val rel = f.relativeTo(repoRoot()).path
                    if (rel in SOURCE_ALLOWLIST) return@forEach // 白名单文件整体放行
                    f.readLines().forEachIndexed { i, raw ->
                        val code = raw.trim()
                        if (code.startsWith("//") || code.startsWith("*")) return@forEachIndexed // 跳过注释行
                        BANNED_SOURCE_TOKENS.firstOrNull { code.contains(it) }?.let { tok ->
                            violations += "$rel:${i + 1}: 命中 '$tok' -> ${code.take(120)}"
                        }
                    }
                }
        }
        assertTrue(
            "发现疑似网络调用（红线 #8）。若确属必要，请在 SOURCE_ALLOWLIST 显式登记并评审：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ===== 常量 / 辅助 =====

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("找不到 settings.gradle.kts（repoRoot 解析失败）")
    }

    /** AGP 合并产物中的 App（非 UnitTest 变体）Manifest。 */
    private fun mergedAppManifests(): List<File> {
        val base = File(repoRoot(), "app/build/intermediates")
        if (!base.exists()) return emptyList()
        return base.walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .filter { p -> p.path.contains("merged_manifest") || p.path.contains("merged_manifests") }
            .filterNot { it.path.contains("UnitTest") }
            .toList()
    }

    private companion object {
        /** 禁止的网络 / 连接类权限（含 INTERNET 这个能联网的充要权限）。 */
        val BANNED_PERMISSIONS = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        )

        /** 禁止的网络 / 分析 / 广告库（直接依赖）。Coil/Readium 不在此列（本地用）。 */
        val BANNED_LIBS = listOf(
            "okhttp", "retrofit", "volley",
            "firebase", "crashlytics", "play-services-ads",
            "sentry", "bugsnag", "amplitude", "mixpanel",
        )

        /** 禁止的源码网络调用特征（库前缀 / 平台原语）。 */
        val BANNED_SOURCE_TOKENS = listOf(
            "okhttp3.",
            "retrofit2.",
            "com.android.volley",
            "HttpURLConnection",
            "org.readium.r2.shared.util.http",
            "DefaultHttpClient",
            "Firebase",
            "Crashlytics",
            "Socket(",
        )

        /**
         * 显式白名单：唯一允许的网络能力是 Readium 的 HTTP 客户端。
         * 安全理由：(a) 发布 Manifest 无 INTERNET 权限（见 shippedMergedManifest 测试），套接字打不开；
         * (b) 仅用于远程资源/封面，本地 EPUB/TXT 打开路径（OpenBookUseCase）从不触发它。
         */
        val SOURCE_ALLOWLIST = setOf(
            "reader/readium/src/main/java/com/xuziyue/ebook/reader/readium/ReadiumFacade.kt",
        )
    }
}
