package com.xuziyue.ebook

import android.app.Application
import android.net.Uri
import com.xuziyue.ebook.data.AppSettingsRepository
import com.xuziyue.ebook.data.BookFileImporter
import com.xuziyue.ebook.data.scan.ScanDirectoryUseCase
import com.xuziyue.ebook.log.CrashLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 应用入口。@HiltAndroidApp 触发 Hilt 的依赖注入代码生成。
 *
 * SET-05：onCreate 中种植 Timber（debug → logcat）+ 安装崩溃日志处理器（脱敏，红线 #8）。
 * 崩溃日志开关默认关（红线 #8：仅在明确同意后启用）；[crashLogEnabled] 缓存到 @Volatile，
 * 崩溃瞬间同步读，不做异步 DataStore（崩溃时协程可能已不可用）。
 * REL-04：onCreate 清理崩溃残留的导入临时文件（设计 §7：App 启动时清理过期临时文件）。
 * IMP-06：onCreate 末尾后台静默增量扫描授权目录（自动扫描开关开且有授权时；日志只记数量，红线 #8）。
 */
@HiltAndroidApp
class EbookApp : Application() {

    @Inject
    lateinit var appSettings: AppSettingsRepository

    @Inject
    lateinit var bookFileImporter: BookFileImporter

    @Inject
    lateinit var scanDirectoryUseCase: ScanDirectoryUseCase

    /** 应用级协程作用域（DataStore 开关缓存订阅用）。 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 崩溃日志开关缓存（崩溃瞬间同步读，不做异步 DataStore）。 */
    @Volatile
    private var crashLogEnabled = false

    override fun onCreate() {
        super.onCreate() // Hilt 完成字段注入
        // REL-04：清理崩溃残留的导入临时文件（importing-*.tmp），不留半成品。
        bookFileImporter.cleanupStaleTempFiles()
        // debug 构建种植 DebugTree（logcat，设备本地，不落文件）。
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // 缓存崩溃日志开关到 @Volatile（崩溃瞬间同步读）。
        appSettings.crashLogEnabled
            .onEach { crashLogEnabled = it }
            .launchIn(applicationScope)
        // 安装崩溃处理器：开关开时写脱敏日志，始终链式交给系统默认 handler。
        CrashLogger.install(this) { crashLogEnabled }
        // IMP-06：冷启动自动扫描授权目录（后台静默；书库 Room Flow 自动回推新书目）。
        applicationScope.launch {
            val autoScan = appSettings.importAutoScan.first()
            val treeUri = appSettings.importTreeUri.first()
            if (autoScan && treeUri != null) {
                // 授权失效（SecurityException → null）不提示不打扰，等用户手动重扫时再引导重新授权。
                // 日志只记数量，不记文件名（红线 #8）。
                runCatching { scanDirectoryUseCase.scan(Uri.parse(treeUri)) }
                    .onSuccess { report ->
                        Timber.i(
                            "IMP06 冷启动扫描完成：imported=%d exists=%d skipped=%d failed=%d",
                            report?.imported ?: 0, report?.alreadyExists ?: 0,
                            report?.skippedUnchanged ?: 0, report?.failed ?: 0,
                        )
                    }
                    .onFailure { Timber.w(it, "IMP06 冷启动扫描失败") }
            }
        }
    }
}
