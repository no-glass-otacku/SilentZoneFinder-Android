import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

// google-services.json 파일이 존재하는지 확인하고 조건부로 플러그인 적용
// Google Services 플러그인이 검색하는 여러 위치 확인
val googleServicesFile = listOf(
    File(projectDir, "google-services.json"),           // app/google-services.json
    File(projectDir, "src/main/google-services.json"),  // app/src/main/google-services.json
    File(projectDir, "src/debug/google-services.json"), // app/src/debug/google-services.json
    File(projectDir, "src/release/google-services.json") // app/src/release/google-services.json
).firstOrNull { it.exists() }

if (googleServicesFile != null) {
    apply(plugin = "com.google.gms.google-services")
}

// local.properties에서 Supabase 키 읽기
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.example.silentzonefinder_android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.silentzonefinder_android"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase 환경 변수 (local.properties에서 읽기)
        val supabaseUrl = localProperties.getProperty("supabase.url", "")
        val supabaseAnonKey = localProperties.getProperty("supabase.anon.key", "")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        
        // Kakao 환경 변수 (local.properties에서 읽기)
        val kakaoRestApiKey = localProperties.getProperty("kakao.rest.api.key", "")
        val kakaoNativeAppKey = localProperties.getProperty("kakao.native.app.key", "")
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestApiKey\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        manifestPlaceholders["KAKAO_REST_API_KEY"] = kakaoRestApiKey
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
        
        // OpenWeatherMap 환경 변수 (local.properties에서 읽기)
        val openWeatherMapApiKey = localProperties.getProperty("openweathermap.api.key", "")
        buildConfigField("String", "OPENWEATHERMAP_API_KEY", "\"$openWeatherMapApiKey\"")
        
        // NDK 아키텍처 필터 (카카오맵 SDK 네이티브 라이브러리 포함)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    implementation("io.coil-kt:coil:2.6.0")


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    
    // Supabase v3 (BOM + 개별 모듈)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.1"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    
    // Ktor 엔진/플러그인 (Supabase + Kakao API)
    implementation(platform("io.ktor:ktor-bom:3.0.0"))
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-websockets")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    
    // Coroutines for lifecycleScope
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Kakao Map SDK
    implementation("com.kakao.maps.open:android:2.12.8")
    
    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}