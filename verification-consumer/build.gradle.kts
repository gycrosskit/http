plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
}
val httpVersion = providers.gradleProperty("httpVersion").orElse("1.0.0").get()
kotlin {
    androidTarget { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    iosArm64(); iosX64()
    iosSimulatorArm64 { binaries.framework { baseName = "HttpConsumer"; isStatic = true } }
    ohosArm64()
    sourceSets {
        commonMain.dependencies { implementation("com.github.gycrosskit.http:http-core:$httpVersion") }
    }
}
android {
    namespace = "io.github.gycrosskit.http.consumer"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
