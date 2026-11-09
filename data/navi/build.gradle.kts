plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.untilparking.data.navi"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))
    // NaviHelper SDK를 import하는 유일한 모듈
    implementation(libs.pleos.navihelper)
    implementation(libs.kotlinx.coroutines.android)
}
