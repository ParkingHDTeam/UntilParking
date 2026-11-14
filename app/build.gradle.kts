import com.android.build.api.variant.ResValue
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 자격 증명은 저장소에 두지 않는다. local.properties(.gitignore 대상)에서 읽어 BuildConfig로만 넘긴다.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun localValue(key: String): String = "\"${localProperties.getProperty(key).orEmpty()}\""

android {
    namespace = "app.untilparking"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "app.untilparking"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "PLEOS_CLIENT_ID", localValue("pleos.clientId"))
        buildConfigField("String", "PLEOS_CLIENT_SECRET", localValue("pleos.clientSecret"))
        // 공공데이터포털 Decoding 키(전국주차장정보표준데이터). pleos 플레이버만 읽는다
        buildConfigField("String", "PARKING_SERVICE_KEY", localValue("parking.serviceKey"))
    }

    // 기획서 27절: mock = 가상 데이터와 차량 상태(폰 에뮬레이터용), pleos = 실제 Pleos SDK
    flavorDimensions += "env"
    productFlavors {
        create("mock") {
            dimension = "env"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
        }
        create("pleos") {
            dimension = "env"
        }
    }

    buildTypes {
        release {
            optimization { enable = false }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        // AGP 9부터 기본 꺼짐. 아래 androidComponents가 app_name을 만들 때 쓴다
        resValues = true
    }
}

// 기기에 깔린 변형을 런처에서 구분하도록 앱 이름 뒤에 mock 여부와 빌드 타입을 붙인다(예: "주차까지 mock debug")
androidComponents {
    onVariants { variant ->
        val tags = listOfNotNull("mock".takeIf { variant.flavorName == "mock" }, variant.buildType)
        variant.resValues.put(
            variant.makeResValueKey("string", "app_name"),
            ResValue((listOf("주차까지") + tags).joinToString(" ")),
        )
    }
}

dependencies {
    // 조립 지점(Composition Root): 모든 구현 모듈을 아는 유일한 모듈
    implementation(project(":domain"))
    implementation(project(":usecase"))
    implementation(project(":feature:parking"))
    implementation(project(":data:parking"))
    implementation(project(":data:vehicle"))
    implementation(project(":data:notification"))
    // Pleos SDK 어댑터는 pleos 플레이버에만 들어간다. mock APK에는 SDK가 없다.
    "pleosImplementation"(project(":data:navi"))
    "pleosImplementation"(project(":data:voice"))
    "pleosImplementation"(project(":data:driving"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
