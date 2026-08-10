package com.xuziyue.ebook

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。@HiltAndroidApp 触发 Hilt 的依赖注入代码生成。
 */
@HiltAndroidApp
class EbookApp : Application()
