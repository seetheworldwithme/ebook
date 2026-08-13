package com.xuziyue.ebook

import com.xuziyue.ebook.settings.LicenseData
import com.xuziyue.ebook.settings.LicenseData.Entry
import com.xuziyue.ebook.settings.LicenseData.License
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REL-07 许可证清单防回退测试（design.md §11.7 发布门槛，红线 #7 覆盖传递依赖）。
 *
 * 把「手写 [LicenseData] 与实际依赖树核对结论」**固化成断言**：将来谁移除了 jsoup / Media3 /
 * PdfiumAndroid 等已识别的传递依赖披露，或删了某个许可证全文 asset，对应断言即红，强制走人工评审
 * （更新本测试或补回披露）。完整审核表见 `docs/REL-07-许可证隐私数据安全审核.md`。
 *
 * 注意：本测试是「已识别库必须在清单里」的存在性断言，不是对 gradle 依赖树的实时解析
 * （JVM 单测不调 gradle）。依赖树变化时由人工更新本测试的期望集合 + 审核文档，与 EpubSecurityValidator
 * 程序化夹具同属「固化人工审核结论」范式。
 */
class LicenseDataAuditTest {

    /** 1. 每个 License 全文 asset 必须真实存在于 assets/legal/。 */
    @Test
    fun everyLicenseAssetFileExists() {
        License.entries.forEach { lic ->
            val asset = File(repoRoot(), "app/src/main/assets/${lic.assetPath}")
            assertTrue(
                "许可证全文缺失：${lic.assetPath}（${lic.displayName}）。应在 app/src/main/assets/ 下补全文。",
                asset.exists() && asset.length() > 0,
            )
        }
    }

    /** 2. 清单必须覆盖全部出现的许可证族（Apache / BSD / GPL-CPE / MIT），缺一即漏披露。 */
    @Test
    fun allLicenseFamiliesRepresented() {
        val usedFamilies = LicenseData.entries.map { it.license }.toSet()
        License.entries.forEach { expected ->
            assertTrue(
                "许可证族 ${expected.displayName} 无任何库使用——要么清理未用枚举，要么补回对应库披露",
                expected in usedFamilies,
            )
        }
    }

    /**
     * 3. 已识别的「必须披露」库必须在清单里（含传递依赖）。
     *
     * 名单取自 2026-08-13 依赖树核对（[docs/REL-07-许可证隐私数据安全审核.md]）。
     * 非实质 / 同族归并的 AndroidX 子模块与注解桥接库（jsr305/jspecify/javax.inject）不在此列。
     */
    @Test
    fun knownMustDiscloseLibrariesAreListed() {
        MUST_DISCLOSE.forEach { token ->
            assertTrue(
                "必须披露的库未在清单：'$token'（REL-07 审核已识别，应在 LicenseData 补回）",
                LicenseData.entries.any { token in it.name },
            )
        }
    }

    /** 4. 条目名不得重复（防手写清单笔误）。 */
    @Test
    fun entryNamesAreUnique() {
        val names = LicenseData.entries.map { it.name }
        assertEquals(
            "LicenseData 出现重名条目：${names.groupingBy { it }.eachCount().filter { it.value > 1 }}",
            names.size,
            names.toSet().size,
        )
    }

    /** 5. 每条目字段完整（版本 / 主页非空）。 */
    @Test
    fun everyEntryHasVersionAndUrl() {
        LicenseData.entries.forEach { e: Entry ->
            assertTrue("条目 ${e.name} 版本为空", e.version.isNotBlank())
            assertTrue("条目 ${e.name} 主页为空", e.url.isNotBlank())
            assertTrue("条目 ${e.name} 许可证为空", e.license.displayName.isNotBlank())
        }
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

    private companion object {
        /**
         * 已识别、必须在 [LicenseData] 披露的库名片段（按 name 子串匹配）。
         * 直连库 + 实质性传递依赖（jsoup MIT / Media3 / PdfiumAndroid 等）。
         */
        val MUST_DISCLOSE = listOf(
            // 直连依赖
            "Readium", "Coil", "Room", "Jetpack Compose", "Hilt / Dagger",
            "AndroidX DataStore", "AndroidX Fragment", "AndroidX Lifecycle",
            "AndroidX Navigation", "AndroidX WebKit", "AndroidX Core KTX",
            "Kotlin Coroutines", "Kotlinx Serialization", "Kotlinx Datetime",
            "Timber", "desugar_jdk_libs",
            // 实质性传递依赖（REL-07 审核新增）
            "jsoup", "Media3", "Guava", "Okio", "Accompanist", "PdfiumAndroid",
            "AndroidPdfViewer", "koi", "AppCompat",
        )
    }
}
