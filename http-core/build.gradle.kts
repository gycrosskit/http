plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    ohosArm64()
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2-1.0.0")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.1-1.0.0")
            // 公开工厂与扩展签名暴露 Ktor 类型，产物消费者须可直接解析。
            api("io.ktor:ktor-client-core:3.3.3-1.1.0-04")
            implementation("io.ktor:ktor-client-content-negotiation:3.3.3-1.1.0-04")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3-1.1.0-04")
        }
        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.3.3-1.1.0-04") }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.3.3-1.1.0-04") }
        ohosArm64Main.dependencies { implementation("io.ktor:ktor-client-curl:3.3.3-1.1.0-04") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2-1.0.0")
            implementation("io.ktor:ktor-client-mock:3.3.3-1.1.0-04")
        }
    }
}

android {
    namespace = "com.dgtang.live.http"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
