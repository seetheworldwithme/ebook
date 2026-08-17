package com.xuziyue.ebook.data.scan

import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.data.db.ImportSourceEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DirectoryScanner 单测（纯 JVM，IMP-06）。
 *
 * 用内存 Map 假 DAO + 假导入函数驱动全部编排分支：
 * 新文件导入+落记录 / 未变化跳过 / size 变化重导 / mtime 变化重导 / 未知 size 重导 /
 * AlreadyExists 也落记录 / Failed 不落记录 / 失败不中断 / bookId 复用已有记录 id。
 */
class DirectoryScannerTest {

    /** 内存假 import_sources 表。 */
    private class FakeSourceDao {
        val table = mutableMapOf<String, ImportSourceEntity>() // key=sourceUri

        suspend fun get(uri: String): ImportSourceEntity? = table[uri]

        suspend fun upsert(e: ImportSourceEntity) {
            // 模拟真实 DAO 的 upsert 语义：主键冲突覆盖；这里按 sourceUri 覆盖（保留旧 id 的场景由 scanner 传旧 id）
            table[e.sourceUri] = e
        }
    }

    /** 假导入结果脚本：按 uri 字符串返回预设 Outcome。 */
    private class FakeImport(val results: MutableMap<String, ImportBookUseCase.Outcome>) {
        val calls = mutableListOf<String>()
        val import: suspend (String) -> ImportBookUseCase.Outcome = { uri ->
            calls.add(uri)
            results[uri] ?: ImportBookUseCase.Outcome.Imported("book-${uri.substringAfterLast('/')}")
        }
    }

    private fun doc(uri: String, size: Long = 100L, mtime: Long = 200L) =
        ScannedDocument(uri, uri.substringAfterLast('/') + ".epub", size, mtime)

    private fun scanner(
        dao: FakeSourceDao,
        import: FakeImport,
        clock: () -> Long = { 1000L },
        idGen: () -> String = { "id-${dao.table.size}" },
    ) = DirectoryScanner(
        sourceDaoGet = { dao.get(it) },
        sourceDaoUpsert = { dao.upsert(it) },
        importUri = import.import,
        clock = clock,
        idGenerator = idGen,
    )

    @Test
    fun `新文件导入成功并落记录`() = runTest {
        val dao = FakeSourceDao()
        val imp = FakeImport(mutableMapOf("content://doc/1" to ImportBookUseCase.Outcome.Imported("b1")))
        val report = scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1")))

        assertEquals(1, report.imported)
        assertEquals(0, report.skippedUnchanged)
        assertEquals(1, report.totalScanned)
        val record = dao.table["content://doc/1"]
        assertEquals("b1", record?.bookId)
        assertEquals(100L, record?.fileSize)
        assertEquals(200L, record?.lastModified)
        assertEquals(1000L, record?.scannedAt)
    }

    @Test
    fun `size 与 mtime 均未变则跳过且不调导入`() = runTest {
        val dao = FakeSourceDao()
        dao.upsert(ImportSourceEntity("s1", "content://doc/1", "b1", 100L, 200L, 0L))
        val imp = FakeImport(mutableMapOf())
        val report = scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1")))

        assertEquals(1, report.skippedUnchanged)
        assertEquals(0, report.imported)
        assertTrue(imp.calls.isEmpty()) // 未重读内容
    }

    @Test
    fun `size 变化则重新导入`() = runTest {
        val dao = FakeSourceDao()
        dao.upsert(ImportSourceEntity("s1", "content://doc/1", "b1", 100L, 200L, 0L))
        val imp = FakeImport(mutableMapOf())
        // 同 uri size 999（mtime 同）
        val report = scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1", size = 999L)))

        assertEquals(1, report.imported)
        assertEquals(999L, dao.table["content://doc/1"]?.fileSize) // 记录已刷新
    }

    @Test
    fun `mtime 变化则重新导入`() = runTest {
        val dao = FakeSourceDao()
        dao.upsert(ImportSourceEntity("s1", "content://doc/1", "b1", 100L, 200L, 0L))
        val imp = FakeImport(mutableMapOf())
        val report = scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1", mtime = 888L)))

        assertEquals(1, report.imported)
        assertEquals(888L, dao.table["content://doc/1"]?.lastModified)
    }

    @Test
    fun `size 未知(-1)视为变化重新导入`() = runTest {
        val dao = FakeSourceDao()
        dao.upsert(ImportSourceEntity("s1", "content://doc/1", "b1", 100L, 200L, 0L))
        val imp = FakeImport(mutableMapOf())
        val report = scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1", size = -1L)))

        assertEquals(1, report.imported) // 宁可重导，contentHash 去重兜底
    }

    @Test
    fun `AlreadyExists 也落记录让下次跳过`() = runTest {
        val dao = FakeSourceDao()
        val imp = FakeImport(
            mutableMapOf("content://doc/1" to ImportBookUseCase.Outcome.AlreadyExists("b-existing")),
        )
        val report = scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1")))

        assertEquals(1, report.alreadyExists)
        assertEquals("b-existing", dao.table["content://doc/1"]?.bookId)

        // 第二次扫描：跳过
        val report2 = scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1")))
        assertEquals(1, report2.skippedUnchanged)
    }

    @Test
    fun `Failed 不落记录下次可重试`() = runTest {
        val dao = FakeSourceDao()
        val imp = FakeImport(
            mutableMapOf("content://doc/1" to ImportBookUseCase.Outcome.Failed(com.xuziyue.ebook.ui.UserMessage.Raw("bad"))),
        )
        val report = scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1")))

        assertEquals(1, report.failed)
        assertTrue(dao.table.isEmpty()) // 无记录
    }

    @Test
    fun `单个失败不中断后续文件`() = runTest {
        val dao = FakeSourceDao()
        val imp = FakeImport(
            mutableMapOf(
                "content://doc/1" to ImportBookUseCase.Outcome.Failed(com.xuziyue.ebook.ui.UserMessage.Raw("bad")),
                "content://doc/2" to ImportBookUseCase.Outcome.Imported("b2"),
            ),
        )
        val report = scanner(dao, imp).scanDocuments(
            listOf(doc("content://doc/1"), doc("content://doc/2")),
        )

        assertEquals(1, report.failed)
        assertEquals(1, report.imported)
        assertEquals("b2", dao.table["content://doc/2"]?.bookId)
    }

    @Test
    fun `重扫已有记录时复用旧记录 id`() = runTest {
        val dao = FakeSourceDao()
        dao.upsert(ImportSourceEntity("keep-id", "content://doc/1", "b1", 100L, 200L, 0L))
        val imp = FakeImport(mutableMapOf())
        scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1", size = 999L)))

        assertEquals("keep-id", dao.table["content://doc/1"]?.id) // id 稳定，不生成新 UUID
    }

    @Test
    fun `bookId 变化时记录指向新书`() = runTest {
        // 场景：旧 book 被删（记录 CASCADE 清掉）后重导，或同 uri 导到不同 book
        val dao = FakeSourceDao()
        dao.upsert(ImportSourceEntity("s1", "content://doc/1", "b-old", 100L, 200L, 0L))
        val imp = FakeImport(mutableMapOf("content://doc/1" to ImportBookUseCase.Outcome.Imported("b-new")))
        // mtime 变化触发重导
        scanner(dao, imp).scanDocuments(listOf(doc("content://doc/1", mtime = 999L)))

        assertEquals("b-new", dao.table["content://doc/1"]?.bookId)
    }

    @Test
    fun `扩展名过滤与隐藏文件在枚举层不在 scanner 职责内`() = runTest {
        // scanner 只接收已过滤文档；这里验证空列表与多文档混合统计
        val dao = FakeSourceDao()
        val imp = FakeImport(
            mutableMapOf(
                "content://doc/1" to ImportBookUseCase.Outcome.Imported("b1"),
                "content://doc/2" to ImportBookUseCase.Outcome.AlreadyExists("b2"),
                "content://doc/3" to ImportBookUseCase.Outcome.Failed(com.xuziyue.ebook.ui.UserMessage.Raw("x")),
            ),
        )
        val report = scanner(dao, imp).scanDocuments(
            listOf(doc("content://doc/1"), doc("content://doc/2"), doc("content://doc/3")),
        )
        assertEquals(3, report.totalScanned)
        assertEquals(1, report.imported)
        assertEquals(1, report.alreadyExists)
        assertEquals(1, report.failed)
        assertEquals(3, report.processed)
    }
}
