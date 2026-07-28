plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.graduation_project"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.graduation_project"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "server"
    productFlavors {
        create("local") {
            dimension = "server"
            // 로컬 네트워크 서버 연결 (USB 실기기: adb reverse tcp:8080 tcp:8080 필요)
            buildConfigField("String", "BASE_URL", "\"http://localhost:8080\"")
        }
        create("prod") {
            dimension = "server"
            buildConfigField("String", "BASE_URL", "\"https://echo-prod2.duckdns.org\"")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // VAD (Voice Activity Detection)
    implementation(libs.android.vad.silero)

    // Location
    implementation(libs.play.services.location)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Media3 (ExoPlayer) - 캐릭터 애니메이션 영상 재생
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Health Connect
    implementation(libs.health.connect.client)

    // Location
    implementation(libs.play.services.location)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Security (EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)

    // SQLCipher (Room DB 암호화)
    implementation(libs.sqlcipher.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // Room 마이그레이션 테스트에서 ApplicationProvider(Context) 제공
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// 버전별 Room 스키마 JSON을 내보낸다 (기존에는 exportSchema=false라 과거 버전 스키마가 없었음)
// - 마이그레이션 작성 시 실제 컬럼/타입을 대조하는 참고 자료로 사용
// - 새 마이그레이션 테스트 작성 시 정확한 CREATE TABLE 문의 출처로 사용 (AppDatabaseMigrationTest 참고)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
