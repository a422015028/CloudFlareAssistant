
plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
}

kotlin {
    jvmToolchain(17)
}



android {
    namespace = "com.muort.upworker"
    compileSdk = 36
    buildToolsVersion = "36.1.0"
    
    defaultConfig {
        applicationId = "com.muort.upworker"
        minSdk = 26
        targetSdk = 36
        versionCode = 2609041
        versionName = "7.7.8"
        
        vectorDrawables { 
            useSupportLibrary = true
        }
        
        // Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
    
    signingConfigs {
        create("release") {
            // 优先使用命令行参数（CI/CD），其次使用本地配置
            if (project.hasProperty("android.injected.signing.store.file")) {
                storeFile = file(project.property("android.injected.signing.store.file").toString())
            } else {
                storeFile = file("E:\\AI\\MT.jks")
            }
            if (project.hasProperty("android.injected.signing.store.password")) {
                storePassword = project.property("android.injected.signing.store.password").toString()
            } else {
                storePassword = "861390202"
            }
            if (project.hasProperty("android.injected.signing.key.alias")) {
                keyAlias = project.property("android.injected.signing.key.alias").toString()
            } else {
                keyAlias = "MT"
            }
            if (project.hasProperty("android.injected.signing.key.password")) {
                keyPassword = project.property("android.injected.signing.key.password").toString()
            } else {
                keyPassword = "861390202"
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }
    
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        // HardcodedText / MissingTranslation 等 i18n 拦截规则生效级别：
        //  - lint.xml 中已把核心规则设为 error
        //  - abortOnError=true：一旦有**未在基线中**的 error/warning-as-error 则构建立即失败（CI 级回归防护）
        abortOnError = true
        // 警告也作为失败（与 warningsAsErrors=true 配合，避免新引入的 HardcodedText 被作为 warning 溜过）
        warningsAsErrors = true
        // 跨模块依赖的库内 lint 检查（本项目单模块，true/false 影响不大但保留默认 true）
        checkDependencies = true
        // 基线白名单：所有"历史遗留"lint issue 登记在这里，不触发失败。
        // 由 `./gradlew :app:updateLintBaseline` 生成与更新。
        baseline = file("lint-baseline.xml")
        // 生成 HTML + XML 报告（CI 存档用；按 variant 自动命名：lint-results-{debug,release}.html/xml，默认输出到 $buildDir/reports/）
        htmlReport = true
        xmlReport = true
        // 发布构建也跑 lint（release 发布前拦截）
        checkReleaseBuilds = true
    }

    // App Bundle 语言分拆配置：
    // 本项目在运行时通过 LocaleHelper + DisplaySizeHelper（setLocale / createConfigurationContext）
    // 实现应用内语言切换按钮（跟随系统 / 简体中文 / English），并没有接入 Play Core 的
    // SplitInstallManager 动态语言下载功能。
    //
    // Lint 规则 AppBundleLocaleChanges 要求：
    //   要么 1) 使用 Play Core 下载额外语言（SplitInstallManager），
    //   要么 2) 在 App Bundle 中关闭按语言分拆，即所有语言资源打进主 APK。
    // 我们选择方案 2：中文 + 英文合计仅 2 种，体积可忽略，兼容性最高。
    // 参考：https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    
    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    
    // Retrofit & Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Timber for Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // MPAndroidChart - 数据可视化图表库
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // AWS SDK for S3 (for R2)
    implementation("com.amazonaws:aws-android-sdk-s3:2.81.1")
    implementation("com.amazonaws:aws-android-sdk-core:2.81.1")
    
    // BouncyCastle: Blake3-128 等现代摘要算法（运行时 + 单元测试共享）
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.78")
    testImplementation("io.mockk:mockk:1.13.12")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
