plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.xuziyue.ebook.reader.readium"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 用 api 让 :app 传递访问 Readium；P0V-02 在此封装打开流程与（未来）Compose↔Fragment 桥接。
    api(libs.bundles.readium)
    api(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // 提供真实 org.json 实现（同 :app，规避 android.jar 的 org.json stub）。
    testImplementation("org.json:json:20240303")
}
