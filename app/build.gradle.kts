plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Room schema 导出（红线 #6）：编译时生成 app/schemas/.../BookDatabase/1.json，
// 供 migration 测试读取；room.generateKotlin=true 生成 Kotlin DAO 实现。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// 锁 androidx.lifecycle 全族到 2.10.0：避免被传递依赖（hilt-navigation-compose 1.4.0 等）拉到 2.11.0，
// 后者要求 compileSdk 37 / AGP 9.1.0；本项目锁定 compileSdk 36 / AGP 9.0.0（与 Readium 3.3.0 一致）。
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.lifecycle") {
            useVersion("2.10.0")
        }
    }
}

android {
    namespace = "com.xuziyue.ebook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xuziyue.ebook"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // SET-01：只保留简中 / 英文资源，裁剪依赖库带入的其它语言（缩小 APK）。
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Room schema JSON 读取：androidTest（migration 仪器测试 assets）+ test（SchemaExportedTest resources）。
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        // TYPE-04：排版样本（ruby/rtl/vertical）打入 androidTest assets，供 TypographySamplesOpenTest 开书冒烟读取。
        getByName("androidTest").assets.srcDir("${rootProject.projectDir}/samples/public/typography")
        getByName("test").resources.srcDir("$projectDir/schemas")
    }

    // Robolectric（DAO 单测）需要 Android 资源。
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":reader:readium"))
    // V1 PDF：app 层直接引用 PdfiumEngineProvider / PdfNavigatorFragment / ImageNavigatorFragment
    //（经 :reader:readium 的 api 传递理论上可达，但 KSP 解析失败，显式声明更稳）。
    implementation(libs.readium.adapter.pdfium.navigator)
    // V1 PDF 踩坑：pdfium-navigator 的 POM 把 barteksc 两件套声明为 runtime scope，KSP 解析
    // PdfiumEngineProvider 完整签名（Listener.onConfigurePdfView(PDFView.Configurator)）需要编译期
    // 可见，须显式补 compile 依赖（同 READ-10 media3-common-ktx 的 runtime-scope 坑）。
    implementation(libs.pdfium.android.pdfviewer)
    implementation(libs.pdfium.android.core)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)

    testImplementation(libs.junit)
    // 提供真实 org.json 实现：Android unit test 默认用 android.jar 的 org.json stub（方法抛 not mocked），
    // 而 PersistedLocator / Locator 的序列化测试依赖真实 JSONObject 行为。
    testImplementation("org.json:json:20240303")
    // Room DAO 单测：Robolectric 提供 JVM 上的真实 SQLite，让 in-memory Room 进 testDebugUnitTest（CI 友好）。
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    // Room migration 仪器测试（需真机，CI 跳过；注记说明）。
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
