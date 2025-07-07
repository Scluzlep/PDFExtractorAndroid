plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.scluzlep.pdfnamer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scluzlep.pdfnamer"
        minSdk = 24
        targetSdk = 34
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

    // 将 compileOptions 移动到 android { ... } 内部
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // 为 Kotlin 代码添加 jvmTarget 配置
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // 将所有打包规则合并到一个代码块，并移动到 android { ... } 内部
    packaging {
        resources {
            // 忽略所有重复的元数据文件，这是处理 PDFBox 等库的标准做法
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    // 安卓标准库
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

// 引入专门为安卓适配的 PDFBox 库，它不依赖 java.awt
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // 测试库
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}