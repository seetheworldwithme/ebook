pluginManagement {
    repositories {
        // 国内镜像优先：规避 dl.google.com 网络问题 + 加速
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/jitpack.io")
        google()
        mavenCentral()
        // 必需：readium-streamer 传递依赖 com.mcxiaoke.koi:core:0.5.5；
        //       readium-adapter-pdfium 传递依赖 com.github.marain87:AndroidPdfViewer:3.2.8
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "EbookReader"
include(":app")
include(":core:model")
include(":reader:readium")
