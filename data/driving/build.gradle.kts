plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.untilparking.data.driving"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // AAOS android.car(CarUxRestrictionsManager)를 쓰는 유일한 모듈
    useLibrary("android.car")
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.android)
}
