pluginManagement {
    repositories {
        // 使用国内镜像，确保下载成功
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // 在这里，我们为整个项目定义所有插件的“唯一版本号”
    // 这是解决所有版本冲突的最终方案
    plugins {
        id("com.android.application") version "8.11.0"
        id("org.jetbrains.kotlin.android") version "1.9.20"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "PDFExtractorAndroid"
include(":app")