plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // 여것도 파이어 베이스 프로젝트 빌드거기에도 id로 시작되는거 있을겨 그것도 ㅇㅇ
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.myapplication"

    // 에러 해결을 위해 36으로 상향 조정
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24

        // 에러 해결을 위해 36으로 상향 조정
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        // XML 레이아웃(View)과 Compose를 동시에 쓸 수 있도록 설정
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.0.1")
    // 1. Retrofit & Gson (날씨 API 통신용)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.google.android.gms:play-services-location:21.0.1")
    // 2. XML 레이아웃 및 AppCompat 라이브러리 (기본 UI 구성용)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 3. 기본 및 Compose 관련 라이브러리 (기존 프로젝트 설정 유지)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // 테스트용 라이브러리
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.google.android.material:material:1.9.0")
// 이 줄이 있어야 드로어가 작동합니다.
    // 파이어베이스꺼 문제있음 여기를 봐라
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.firebase:firebase-bom:34.13.0")
    implementation("com.google.firebase:firebase-analytics:23.2.0")
    implementation("com.google.firebase:firebase-auth-ktx:23.2.1")     // 로그인용
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.4") // 권한 저장용
    implementation("androidx.core:core-ktx:1.12.0")}