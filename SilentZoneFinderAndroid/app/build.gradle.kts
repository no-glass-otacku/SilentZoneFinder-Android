import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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
    
    // Ktor Android 엔진 (Supabase가 내부적으로 사용)
    implementation(platform("io.ktor:ktor-bom:3.0.0"))
    implementation("io.ktor:ktor-client-android")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-content-negotiation")
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
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}