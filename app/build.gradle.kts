
plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.muort.upworker"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.muort.upworker"
        minSdk = 25
        targetSdk = 34
        versionCode = 2025121350
        versionName = "5.0"
        
        vectorDrawables { 
            useSupportLibrary = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    buildTypes {
    getByName("release") {
        isMinifyEnabled = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}

    buildFeatures {
        viewBinding = true
        
    }
    
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.9.3") // 最后一个 Kotlin 1.7 编译的版本
    implementation("com.squareup.okio:okio:3.2.0")     // 匹配的 okio 版本
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
