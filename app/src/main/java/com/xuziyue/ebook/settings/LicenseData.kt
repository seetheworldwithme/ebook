package com.xuziyue.ebook.settings

/**
 * 第三方依赖许可证清单（design.md §4.6 SET-05，红线 #7）。
 *
 * 手写清单（不用 play-services-oss-licenses——会引入网络相关依赖，违反 SET-04 离线红线）。
 * 每条含库名 / 版本 / 许可证类型 / 主页。许可证全文存 assets/legal/，运行时按需读取。
 *
 * **审核口径（REL-07，2026-08-13）**：本清单依据 `./gradlew :app:dependencies
 * --configuration releaseRuntimeClasspath` 实际解析树核对，覆盖全部随 APK 发布的**实质性**
 * 第三方库（含 Readium / Coil 等的传递依赖）。AndroidX 同族传递子模块（annotation / collection /
 * savedstate / lifecycle-* / core-viewtree 等数十个）与 Kotlin stdlib 均为 Apache 2.0，按家族
 * 归并披露，不逐子模块列出（见 `docs/REL-07-许可证隐私数据安全审核.md` 全量表）。体积极小的纯注解
 * 桥接库（jsr305 / jspecify / javax.inject / jakarta.inject）在审核文档登记、不入本清单。
 */
object LicenseData {

    /** 许可证类型 → 全文 assets 路径。 */
    enum class License(val displayName: String, val assetPath: String) {
        APACHE_2_0("Apache License 2.0", "legal/apache-2.0.txt"),
        BSD_3_CLAUSE("BSD 3-Clause \"New\" or \"Revised\" License", "legal/bsd-3-clause.txt"),
        GPL_CPE("GNU GPL v2 with Classpath Exception", "legal/gpl-cpe.txt"),
        MIT("MIT License", "legal/mit.txt"),
    }

    data class Entry(
        val name: String,
        val version: String,
        val license: License,
        val url: String,
        /** 传递依赖标记（Readium / Coil 等的传递依赖，非本工程直接声明）。 */
        val transitive: Boolean = false,
    )

    /** 全部依赖（按名称排序）。 */
    val entries: List<Entry> = listOf(
        Entry("Accompanist DrawablePainter", "0.37.3", License.APACHE_2_0, "https://github.com/google/accompanist", transitive = true),
        Entry("AndroidPdfViewer (marain87)", "3.2.8", License.APACHE_2_0, "https://github.com/marain87/PdfViewer", transitive = true),
        Entry("AndroidX AppCompat", "1.7.1", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx", transitive = true),
        Entry("AndroidX Core KTX", "1.18.0", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX DataStore Preferences", "1.1.7", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX Fragment", "1.8.9", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX Lifecycle", "2.10.0", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX Media3 (ExoPlayer)", "1.10.0", License.APACHE_2_0, "https://developer.android.com/media/media3", transitive = true),
        Entry("AndroidX Navigation Compose", "2.9.7", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX Room", "2.8.4", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("AndroidX WebKit", "1.15.0", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("Coil", "3.5.0", License.APACHE_2_0, "https://coil-kt.github.io/coil/"),
        Entry("desugar_jdk_libs", "2.1.5", License.GPL_CPE, "https://github.com/google/desugar_jdk_libs"),
        Entry("Guava", "33.3.1-android", License.APACHE_2_0, "https://github.com/google/guava", transitive = true),
        Entry("Hilt / Dagger", "2.60.1", License.APACHE_2_0, "https://dagger.dev/hilt/"),
        Entry("Hilt Navigation Compose", "1.2.0", License.APACHE_2_0, "https://developer.android.com/jetpack/androidx"),
        Entry("Jetpack Compose", "BOM 2026.06.01", License.APACHE_2_0, "https://developer.android.com/jetpack/compose"),
        Entry("jsoup", "1.22.2", License.MIT, "https://jsoup.org/", transitive = true),
        Entry("Kotlin Coroutines", "1.10.2", License.APACHE_2_0, "https://github.com/Kotlin/kotlinx.coroutines"),
        Entry("Kotlinx Datetime", "0.7.1", License.APACHE_2_0, "https://github.com/Kotlin/kotlinx-datetime"),
        Entry("Kotlinx Serialization", "1.10.0", License.APACHE_2_0, "https://github.com/Kotlin/kotlinx.serialization"),
        Entry("koi (mcxiaoke)", "0.5.5", License.APACHE_2_0, "https://github.com/mcxiaoke/koi", transitive = true),
        Entry("Okio", "3.17.0", License.APACHE_2_0, "https://github.com/square/okio", transitive = true),
        Entry("PdfiumAndroid (marain87)", "1.9.8", License.APACHE_2_0, "https://github.com/marain87/PdfiumAndroid", transitive = true),
        Entry("Readium Kotlin Toolkit", "3.3.0", License.BSD_3_CLAUSE, "https://github.com/readium/mobile"),
        Entry("Timber", "5.0.1", License.APACHE_2_0, "https://github.com/JakeWharton/timber"),
    )
}
