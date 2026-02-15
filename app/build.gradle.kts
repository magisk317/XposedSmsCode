import java.io.FileInputStream
import java.security.SecureRandom
import java.util.Properties
import java.util.TimeZone
import java.util.Date
import java.text.SimpleDateFormat

plugins {
    alias(libs.plugins.android.application)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.ksp)
    id(libs.plugins.kotlin.compose.get().pluginId)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

val keystoreFilePath = System.getenv("KEYSTORE_FILE") ?: findProperty("tianma.keystore.path")?.toString() ?: "release.jks"
val keyFile = file(keystoreFilePath)
val propertyFile = file(findProperty("tianma.signature.path") ?: "signature.properties")

val keyProps = Properties()
if (propertyFile.exists()) {
    FileInputStream(propertyFile).use { keyProps.load(it) }
}

val isSigningInfoAvailable = keyFile.exists() &&
    (keyProps.getProperty("STORE_PASSWORD") != null || System.getenv("STORE_PASSWORD") != null)

fun releaseTime(): String {
    return SimpleDateFormat("yyMMdd").apply { timeZone = TimeZone.getDefault() }.format(Date())
}

fun randomHex8(): String {
    val r = SecureRandom()
    return String.format("%08x", r.nextInt().toLong() and 0xffffffffL)
}

val versionNameStr = libs.versions.versionName.get()
val versionCodeInt = libs.versions.versionCode.get().toInt()
val compileSdkStr = libs.versions.compileSdk.get()
val minSdkInt = libs.versions.minSdk.get().toInt()
val targetSdkStr = libs.versions.targetSdk.get()
val sdkExtensionInt = libs.versions.compileSdkExtension.get().toInt()
val ndkVersionStr = libs.versions.ndk.get()

fun releaseBaseName(versionName: String): String {
    return "XposedSmsCode_v${versionName.replace("\\s+".toRegex(), "_")}_${releaseTime()}"
}

fun releaseApkName(versionName: String, buildType: String, abiSuffix: String): String {
    return "${releaseBaseName(versionName)}_${buildType}_${abiSuffix}.apk"
}

fun releaseAabName(versionName: String): String {
    return "${releaseBaseName(versionName)}_release.aab"
}

android {
    namespace = "com.github.tianma8023.xposed.smscode"
    
    val sdkCodename = compileSdkStr.removePrefix("android-")
    val sdkAsInt = sdkCodename.toIntOrNull()
    if (sdkAsInt != null) {
        compileSdk = sdkAsInt
    } else {
        compileSdkPreview = sdkCodename
    }
    
    compileSdkExtension = sdkExtensionInt
    ndkVersion = ndkVersionStr

    androidResources {
        localeFilters.addAll(listOf("en", "zh-rCN", "zh-rTW"))
    }

    defaultConfig {
        applicationId = "com.github.tianma8023.xposed.smscode"
        minSdk = minSdkInt
        
        val targetSdkCodename = targetSdkStr.removePrefix("android-")
        val targetSdkAsInt = targetSdkCodename.toIntOrNull()
        if (targetSdkAsInt != null) {
            targetSdk = targetSdkAsInt
        } else {
            targetSdkPreview = targetSdkCodename
        }
        
        versionCode = versionCodeInt
        versionName = versionNameStr

        buildConfigField("String", "LOG_TAG", "\"XSmsCode\"")
        buildConfigField("int", "MODULE_VERSION", "$versionCodeInt")
    }

    splits {
        abi {
            isEnable = hasProperty("buildSplits")
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            storeFile = keyFile
            storePassword = System.getenv("STORE_PASSWORD") ?: keyProps.getProperty("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: keyProps.getProperty("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD") ?: keyProps.getProperty("KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("int", "LOG_LEVEL", "2")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")
            if (isSigningInfoAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("alpha") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            buildConfigField("int", "LOG_LEVEL", "2")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")

            if (isSigningInfoAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            buildConfigField("int", "LOG_LEVEL", "4")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")
            if (isSigningInfoAvailable) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            lint {
                disable += "MissingTranslation"
                checkReleaseBuilds = false
            }
            packaging {
                resources {
                    excludes += "**/*.kotlin_*"
                }
            }
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

androidComponents {
    onVariants(selector().all()) { variant ->
        val isDebug = variant.buildType == "debug"
        val suffix = if (isDebug) randomHex8() else ""
        val vName = if (isDebug) "$versionNameStr-$suffix" else versionNameStr
        
        variant.outputs.forEach { output ->
            if (isDebug) {
                output.versionName.set(vName)
            }
            val abi = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier ?: "universal"
            // Use reflection or search for the property if outputFileName is unresolved
            try {
                @Suppress("UNCHECKED_CAST")
                val outputFileName = output.javaClass.getMethod("getOutputFileName").invoke(output) as org.gradle.api.provider.Property<String>
                outputFileName.set(releaseApkName(vName, variant.buildType ?: "", abi))
            } catch (e: Exception) {
                // Ignore for now, build will fail if this is wrong
            }
        }
    }
}

tasks.register("renameReleaseAab") {
    dependsOn("bundleRelease")
    val bundleFileProvider = layout.buildDirectory.file("outputs/bundle/release/app-release.aab")
    val targetFileProvider = layout.buildDirectory.file("outputs/bundle/release/${releaseAabName(versionNameStr)}")
    doLast {
        val bundleFile = bundleFileProvider.get().asFile
        if (bundleFile.exists()) {
            val target = targetFileProvider.get().asFile
            bundleFile.copyTo(target, overwrite = true)
        }
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    finalizedBy("renameReleaseAab")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    implementation(project(":storage"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    compileOnly(libs.xposed.api)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    implementation(libs.play.app.update)
    implementation(libs.haze.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)

    implementation(libs.timber)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.collections.immutable)
}
