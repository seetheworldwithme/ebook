package com.xuziyue.ebook

import android.app.Application
import com.xuziyue.ebook.data.AppSettingsRepository
import com.xuziyue.ebook.data.BookFileImporter
import com.xuziyue.ebook.log.CrashLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

/**
 * 应用入口。@HiltAndroidApp 触发 Hilt 的依赖注入代码生成。
 *
 * SET-05：onCreate 中种植 Timber（debug → logcat）+ 安装崩溃日志处理器（脱敏，红线 #8）。
 * 崩溃日志开关默认关（红线 #8：仅在明确同意后启用）；[crashLogEnabled] 缓存到 @Volatile，
 * 崩溃瞬间同步读，不做异步 DataStore（崩溃时协程可能已不可用）。
 * REL-04：onCreate 清理崩溃残留的导入临时文件（设计 §7：App 启动时清理过期临时文件）。
 */
@HiltAndroidApp
class EbookApp : Application() {

    @Inject
    lateinit var appSettings: AppSettingsRepository

    @Inject
    lateinit var bookFileImporter: BookFileImporter

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
    }
}
