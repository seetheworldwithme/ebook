package com.xuziyue.ebook.settings

/**
 * 第三方依赖许可证清单（design.md §4.6 SET-05，红线 #7）。
 *
 * 手写清单（不用 play-services-oss-licenses——会引入网络相关依赖，违反 SET-04 离线红线）。
 * 每条含库名 / 版本 / 许可证类型 / 主页。许可证全文存 assets/legal/，运行时按需读取。
 *
 * 版本号取自 gradle/libs.versions.toml（2026-08 快照）；Readium 的传递依赖（koi / AndroidPdfViewer）
 * 一并列入（随 APK 发布，红线 #7 要求覆盖传递依赖）。
 */
object LicenseData {

    /** 许可证类型 → 全文 assets 路径。 */
    enum class License(val displayName: String, val assetPath: String) {
        APACHE_2_0("Apache License 2.0", "legal/apache-2.0.txt"),
        BSD_3_CLAUSE("BSD 3-Clause \"New\" or \"Revised\" License", "legal/bsd-3-clause.txt"),
        GPL_CPE("GNU GPL v2 with Classpath Exception", "legal/gpl-cpe.txt"),
    }

    data class Entry(
        val name: String,
        val version: String,
        val license: License,
        val url: String,
        /** 传递依赖标记（Readium 的 koi / AndroidPdfViewer 等）。 */
        val transitive: Boolean = false,
    )

    /** 全部依赖（按名称排序）。 */
    val entries: List<Entry> = listOf(
        Entry("AndroidPdfViewer (marain87)", "3.2.8", License.APACHE_2_0, "https://github.com/marain87/PdfViewer", transitive = true),
        Entry("AndroidX Core KTX", "1.18.0", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX DataStore Preferences", "1.1.7", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX Fragment", "1.8.9", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX Lifecycle", "2.10.0", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX Navigation Compose", "2.9.7", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX Room", "2.8.4", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX WebKit", "1.15.0", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("Coil", "3.5.0", License.APACHE_2_0, "https://coil-kt.github.io/coil/"),
        Entry("desugar_jdk_libs", "2.1.5", License.GPL_CPE, "https://github.com/google/desugar_jdk_libs"),
        Entry("Hilt / Dagger", "2.60.1", License.APACHE_2_0, "https://dagger.dev/hilt/"),
        Entry("Hilt Navigation Compose", "1.2.0", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("Jetpack Compose", "BOM 2026.06.01", License.APACHE_2_0, "https://developer.android.com/jetpack/compose"),
        Entry("Kotlin Coroutines", "1.10.2", License.APACHE_2_0, "https://github.com/Kotlin/kotlinx.coroutines"),
        Entry("Kotlinx Datetime", "0.7.1", License.APACHE_2_0, "https://github.com/Kotlin/kotlinx-datetime"),
        Entry("Kotlinx Serialization", "1.10.0", License.APACHE_2_0, "https://github.com/Kotlin/kotlinx.serialization"),
        Entry("koi (mcxiaoke)", "0.5.5", License.APACHE_2_0, "https://github.com/mcxiaoke/koi", transitive = true),
        Entry("Readium Kotlin Toolkit", "3.3.0", License.BSD_3_CLAUSE, "https://github.com/readium/mobile"),
        Entry("Timber", "5.0.1", License.APACHE_2_0, "https://github.com/JakeWharton/timber"),
    )
}
