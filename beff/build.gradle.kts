import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 인증키는 저장소에 넣지 않는다. 환경변수 또는 local.properties(gitignore)의 이름=값에서 읽는다. 없으면 빈 문자열.
// KAKAO_JS_KEY: 카카오맵 JavaScript 키. 없으면 앱은 카카오맵 배경 없이 OSM 도식 지도만 그린다.
// KAKAO_REST_KEY: 카카오 로컬(장소 검색) REST API 키. 없으면 검색은 앱 안 무장애 데이터에서만 찾는다.
fun secret(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() }
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }.getProperty(name)
    }.orEmpty()

android {
    namespace = "com.beff.app"
    compileSdk = 34

    // AAOS Car API: CarUxRestrictionsManager(주행 제한 상태)
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.beff.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "KAKAO_JS_KEY", "\"${secret("KAKAO_JS_KEY")}\"")
        buildConfigField("String", "KAKAO_REST_KEY", "\"${secret("KAKAO_REST_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.lottie.compose)
    // Pleos Connect SDK — 기본 내비 연동(문서 기준 v3.0.5, 2026-10-19 Nexus 최신)
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // FusedLocation — 가이드 기준 v1.1.3(Nexus 최신). 콘솔 상태 "준비 중"이어도 라이브러리는 받아진다.
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    // Vehicle — 읽기(get)만 쓴다. 제어(set*) 호출은 심사 즉시 탈락이라 쓰지 않는다. 가이드 기준 v2.3.2.
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // 주행 중 음성 안내(S07~S09)용 — 가이드 기준 v3.3.1
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    debugImplementation(libs.androidx.ui.tooling)
}
