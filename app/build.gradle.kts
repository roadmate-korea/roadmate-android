plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 통역 번역 서버 주소(-Proadmate.serverUrl=). release에는 https만 넣을 수 있다(아래 preReleaseBuild 검사)
val serverUrl = (project.findProperty("roadmate.serverUrl") as String?)?.trim().orEmpty()

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
        // 통역 번역 서버(roadmate-AI) 주소. gradle.properties나 -Proadmate.serverUrl=로 넣는다. 비어 있으면 서버 없이 동작한다.
        // 같은 PC에서 AI 서버(127.0.0.1:8000)와 에뮬레이터를 함께 돌리면 http://10.0.2.2:8000
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        // 암호화하지 않은 연결은 기본으로 막는다. debug에서 http 서버 주소를 쓸 때만 연다(아래 buildTypes)
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    buildTypes {
        debug {
            // AI 서버(roadmate-AI)는 http다. debug에서 http 주소를 쓸 때만 암호화하지 않은 연결을 허용한다
            manifestPlaceholders["usesCleartextTraffic"] = serverUrl.startsWith("http://").toString()
        }
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

// release에 http 서버 주소가 들어가지 않게 한다(release는 암호화하지 않은 연결을 막으므로 http면 통역이 모두 실패한다)
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (serverUrl.startsWith("http://")) {
            throw GradleException("release에는 http 서버 주소를 쓸 수 없습니다(roadmate.serverUrl=$serverUrl). https 주소를 쓰거나 비워 두세요.")
        }
    }
}
