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
        minSdk = libs.versions.minSdk.get().toInt()
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
