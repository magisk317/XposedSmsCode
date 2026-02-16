plugins {
    alias(libs.plugins.android.library)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

android {
    namespace = "com.tianma.xsmscode.storage"
    
    val compileSdkStr = libs.versions.compileSdk.get()
    val sdkCodename = compileSdkStr.removePrefix("android-")
    val sdkAsInt = sdkCodename.toIntOrNull()
    if (sdkAsInt != null) {
        compileSdk = sdkAsInt
    } else {
        compileSdkPreview = sdkCodename
    }
    
    compileSdkExtension = libs.versions.compileSdkExtension.get().toInt()

    defaultConfig {
        val minSdkStr = libs.versions.minSdk.get()
        val minSdkCodename = minSdkStr.removePrefix("android-")
        val minSdkAsInt = minSdkCodename.toIntOrNull()
        if (minSdkAsInt != null) {
            minSdk = minSdkAsInt
        } else {
            @Suppress("DEPRECATION")
            minSdkPreview = minSdkCodename
        }
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
    testOptions {
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}
