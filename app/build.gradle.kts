plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.roadmatekorea.android"
    compileSdk = 35
    // 차량 속도(CarPropertyManager). 기기에 있는 라이브러리를 컴파일할 때만 쓴다.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.roadmatekorea.android"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 서버(서버·AI 팀) 주소. gradle.properties나 -Proadmate.serverUrl=로 넣는다. 비어 있으면 서버 기능 없이 동작한다.
        // 서버를 쓸 때는 매니페스트에 INTERNET 권한도 넣어야 한다(서버가 아직 없어 지금은 넣지 않았다)
        buildConfigField("String", "SERVER_URL", "\"${(project.findProperty("roadmate.serverUrl") as String?) ?: ""}\"")
    }

    buildTypes {
        release {
            // R8은 개인정보 로그 제거에만 쓴다(proguard-rules.pro). 이름 바꾸기·코드 줄이기는 하지 않는다
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 공모전 제출용 APK는 디버그 키로 서명한다(설치 확인용). 스토어 배포 시 교체.
            signingConfig = signingConfigs.getByName("debug")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Pleos Connect SDK. FusedLocation은 공공데이터 어린이보호구역 알림에 쓴다(위치는 기기 안에서만).
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.fusedlocation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
