package com.xuziyue.ebook.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SET-01 i18n 防回退测试：默认（简中）与英文 strings.xml 必须一一对应。
 *
 * 把「双 locale key 齐全」固化成测试——将来谁加了中文文案却漏了英文翻译
 * （或反之），对应断言即红，强制补译。沿用 [OfflineGuaranteeTest] 的 repoRoot 定位方式。
 */
class StringResourceKeysTest {

    private fun stringsFile(localeDir: String): File =
        File(repoRoot(), "app/src/main/res/$localeDir/strings.xml")
            .also { assertTrue("strings.xml 应存在：$localeDir", it.exists()) }

    /** 提取 `<string name="key">` 的 key 列表（保持出现顺序，便于检测重复）。 */
    private fun keys(file: File): List<String> {
        val pattern = Regex("""<string\s+name="([^"]+)"""")
        return pattern.findAll(file.readText()).map { it.groupValues[1] }.toList()
    }

    @Test
    fun default_locale_has_no_duplicate_keys() {
        val keys = keys(stringsFile("values"))
        val dups = keys.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue("values/strings.xml 有重复 key：$dups", dups.isEmpty())
    }

    @Test
    fun english_locale_has_no_duplicate_keys() {
        val keys = keys(stringsFile("values-en"))
        val dups = keys.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue("values-en/strings.xml 有重复 key：$dups", dups.isEmpty())
    }

    @Test
    fun english_keys_match_default_keys() {
        val zh = keys(stringsFile("values")).toSet()
        val en = keys(stringsFile("values-en")).toSet()
        // 双向差集：缺译（有中文无英文）或多余（有英文无中文）都算不齐。
        val missingInEn = zh - en
        val extraInEn = en - zh
        assertTrue("英文缺译：$missingInEn", missingInEn.isEmpty())
        assertTrue("英文多余（默认无对应）：$extraInEn", extraInEn.isEmpty())
        assertEquals("key 总数应一致", zh.size, en.size)
    }

    @Test
    fun no_empty_string_values() {
        // 空值（<string name="x"></string> 或仅空白）几乎一定是漏写。
        listOf("values", "values-en").forEach { locale ->
            val text = stringsFile(locale).readText()
            val emptyKeys = Regex("""<string\s+name="([^"]+)"[^>]*>\s*</string>""")
                .findAll(text).map { it.groupValues[1] }.toList()
            assertFalse("$locale/strings.xml 有空值 string：$emptyKeys", emptyKeys.isNotEmpty())
        }
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("找不到 settings.gradle.kts（repoRoot 解析失败）")
    }
}
