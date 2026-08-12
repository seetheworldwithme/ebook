package com.xuziyue.ebook.log

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志捕获 + 脱敏（design.md §4.6 SET-05，红线 #8）。
 *
 * **纯本地，零网络**：崩溃时把 stack trace 脱敏后写到 `filesDir/crash_logs/`，不上传
 *（红线 #8 + SET-04：无 INTERNET 权限，套接字打不开）。
 *
 * 红线 #8 要求：日志不得包含正文、摘录和**完整文件路径**。stack trace 本身不含正文 / 摘录，
 * 但异常消息可能携带书籍文件路径（如 `FileNotFoundException: /data/.../books/xxx.epub`），
 * 故 [sanitize] 负责把路径类信息替换为占位符。
 */
object CrashLogger {

    /** 崩溃日志保留上限（防无限膨胀；超出按时间倒序删除最旧）。 */
    private const val MAX_CRASH_LOGS = 5

    /**
     * 脱敏：移除 / 替换日志中的完整文件路径、敏感目录前缀（红线 #8）。
     *
     * 处理顺序（先后精确匹配、后通用正则兜底）：
     * 1. 应用私有目录前缀（[filesDir] / [cacheDir]）→ `<app-dir>` / `<app-cache>`
     * 2. 通用 Android 存储路径（`/data/data/…`、`/data/user/…`、`/storage/…`）→ 占位符
     * 3. 书籍文件扩展名路径（`*.epub` `*.txt` `*.pdf` `*.cbz`）→ `<file>`
     *
     * 纯函数，便于 [com.xuziyue.ebook.CrashLogSanitizerTest] 单测固化防回退。
     */
    fun sanitize(text: String, filesDir: String, cacheDir: String): String {
        var s = text
        // ① 精确替换应用目录前缀（filesDir / cacheDir 实际值，含 /data/user/0/ 变体）。
        s = s.replace(filesDir, "<app-dir>")
        s = s.replace(cacheDir, "<app-cache>")
        // ② 通用 Android 存储路径兜底（filesDir 未覆盖的中间路径段、其他 App 路径等）。
        s = s.replace(Regex("/data/(?:data|user)/\\d*/[^/\\s]+"), "<app-dir>")
        s = s.replace(Regex("/data/(?:data|user)/[^/\\s]+"), "<app-dir>")
        s = s.replace(Regex("/storage/[^/\\s]+"), "<storage>")
        // ③ 书籍文件扩展名路径。
        s = s.replace(Regex("[/\\w.\\-]+\\.(?:epub|txt|pdf|cbz)", RegexOption.IGNORE_CASE), "<file>")
        return s
    }

    /**
     * 安装全局未捕获异常处理器。崩溃时若 [crashLogEnabled] 返回 true，则写脱敏日志到本地文件；
     * 无论是否写日志，都链式调用原 handler（不吞系统默认崩溃对话框 / 进程退出）。
     *
     * @param context 用于定位 filesDir / crash 目录。
     * @param crashLogEnabled 同步判断开关是否开（崩溃瞬间不能做异步 DataStore 读，由调用方缓存到 @Volatile）。
     */
    fun install(context: Context, crashLogEnabled: () -> Boolean) {
        val filesDir = context.filesDir.absolutePath
        val cacheDir = context.cacheDir.absolutePath
        val crashDir = File(context.filesDir, "crash_logs")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (crashLogEnabled()) {
                writeCrashLog(crashDir, thread, throwable, filesDir, cacheDir)
            }
            // 始终交给系统默认 handler（弹崩溃对话框 + 终止进程），不吞异常。
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 写一条脱敏崩溃日志（同进程内；自身失败不能抛出，否则掩盖原始异常）。 */
    private fun writeCrashLog(
        crashDir: File,
        thread: Thread,
        throwable: Throwable,
        filesDir: String,
        cacheDir: String,
    ) {
        try {
            if (!crashDir.exists()) crashDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(crashDir, "crash_$timestamp.txt")
            val sw = StringWriter()
            sw.append("Time: ").append(Date().toString()).append('\n')
            sw.append("Thread: ").append(thread.name).append('\n')
            throwable.printStackTrace(PrintWriter(sw))
            file.writeText(sanitize(sw.toString(), filesDir, cacheDir))
            // 保留最近 MAX_CRASH_LOGS 条，删除更旧的。
            crashDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_CRASH_LOGS)
                ?.forEach { it.delete() }
        } catch (_: Throwable) {
            // 崩溃处理器自身失败时静默——绝不能掩盖原始异常或干扰进程退出。
        }
    }

    /** 取最近一条崩溃日志文件（供设置页「分享崩溃日志」用）；无则 null。 */
    fun latestCrashLog(context: Context): File? {
        val dir = File(context.filesDir, "crash_logs")
        return dir.listFiles()?.maxByOrNull { it.lastModified() }
    }
}
