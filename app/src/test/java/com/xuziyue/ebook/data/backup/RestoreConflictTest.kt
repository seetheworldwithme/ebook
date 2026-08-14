package com.xuziyue.ebook.data.backup

import com.xuziyue.ebook.data.EpubSecurityValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [classifyConflict] 冲突分类纯函数测试（DATA-03 恢复预览）。
 * 纯 JVM，覆盖四分支边界。
 */
class RestoreConflictTest {

    @Test
    fun `进度更新判定 CONFLICT_PROGRESS_NEWER`() {
        val k = classifyConflict(
            backupProgressMax = 5000L, localProgressMax = 3000L,
            backupNotesMax = 0L, localNotesMax = 0L,
        )
        assertEquals(RestoreUseCase.ConflictKind.CONFLICT_PROGRESS_NEWER, k)
    }

    @Test
    fun `进度相同但笔记更新判定 CONFLICT_NOTES_NEWER`() {
        val k = classifyConflict(
            backupProgressMax = 3000L, localProgressMax = 3000L,
            backupNotesMax = 8000L, localNotesMax = 5000L,
        )
        assertEquals(RestoreUseCase.ConflictKind.CONFLICT_NOTES_NEWER, k)
    }

    @Test
    fun `进度与笔记都不更新判定 UNCHANGED`() {
        val k = classifyConflict(
            backupProgressMax = 3000L, localProgressMax = 5000L, // backup 更旧
            backupNotesMax = 0L, localNotesMax = 1000L,
        )
        assertEquals(RestoreUseCase.ConflictKind.UNCHANGED, k)
    }

    @Test
    fun `进度优先于笔记（两者都更新时判进度）`() {
        val k = classifyConflict(
            backupProgressMax = 9000L, localProgressMax = 3000L,
            backupNotesMax = 8000L, localNotesMax = 5000L,
        )
        assertEquals(RestoreUseCase.ConflictKind.CONFLICT_PROGRESS_NEWER, k)
    }

    @Test
    fun `全 0 视为 UNCHANGED（新装本地空但书已存在）`() {
        val k = classifyConflict(0L, 0L, 0L, 0L)
        assertEquals(RestoreUseCase.ConflictKind.UNCHANGED, k)
    }
}

/**
 * 恢复解压的 Zip Slip 防护测试（DATA-03 / 红线 #4）。
 * 直接验证 [EpubSecurityValidator.isZipSlip]，RestoreUseCase 在 extractFiles 里逐 entry 调用它。
 */
class RestoreZipSlipTest {

    @Test
    fun `合法路径不触发`() {
        assertFalse(EpubSecurityValidator.isZipSlip("books/b1.epub"))
        assertFalse(EpubSecurityValidator.isZipSlip("covers/b1.png"))
        assertFalse(EpubSecurityValidator.isZipSlip("backup.json"))
    }

    @Test
    fun `相对路径遍历触发`() {
        assertTrue(EpubSecurityValidator.isZipSlip("../evil.epub"))
        assertTrue(EpubSecurityValidator.isZipSlip("books/../../evil.epub"))
    }

    @Test
    fun `绝对路径触发`() {
        assertTrue(EpubSecurityValidator.isZipSlip("/etc/evil"))
    }

    @Test
    fun `Windows 风格反斜杠归一化后触发`() {
        assertTrue(EpubSecurityValidator.isZipSlip("..\\evil.epub"))
    }
}
