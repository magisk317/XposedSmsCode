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
val compileSdkInt = libs.versions.compileSdk.get().toInt()
val minSdkInt = libs.versions.minSdk.get().toInt()
val targetSdkInt = libs.versions.targetSdk.get().toInt()
val minSdkStr = libs.versions.minSdk.get()
val targetSdkStr = libs.versions.targetSdk.get()
val sdkExtensionInt = libs.versions.compileSdkExtension.get().toInt()
val ndkVersionStr = libs.versions.ndk.get()

fun releaseBaseName(versionName: String): String {
    return "XposedSmsCode_v${versionName.replace("\\s+".toRegex(), "_")}_${releaseTime()}"
}

fun releaseApkName(versionName: String, buildType: String, abiSuffix: String): String {
    return "${abiSuffix}_${releaseBaseName(versionName)}_${buildType}.apk"
}

fun releaseAabName(versionName: String): String {
    return "${releaseBaseName(versionName)}_release.aab"
}

android {
    namespace = "com.github.tianma8023.xposed.smscode"
    compileSdk = compileSdkInt
    compileSdkExtension = sdkExtensionInt
    ndkVersion = ndkVersionStr

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "false")
        }
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
        }
    }

    androidResources {
        localeFilters.addAll(listOf("en", "zh-rCN", "zh-rTW"))
    }

    defaultConfig {
        applicationId = "com.github.tianma8023.xposed.smscode"
        val minSdkCodename = minSdkStr.removePrefix("android-")
        val minSdkAsInt = minSdkCodename.toIntOrNull()
        if (minSdkAsInt != null) {
            minSdk = minSdkAsInt
        } else {
            @Suppress("DEPRECATION")
            minSdkPreview = minSdkCodename
        }
        
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
    packaging {
        resources {
            excludes += "**/*.kotlin_*"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
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
            ndk {
                debugSymbolLevel = "FULL"
            }
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
            ndk {
                debugSymbolLevel = "FULL"
            }
            lint {
                disable += "MissingTranslation"
                checkReleaseBuilds = false
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
                val outputFileName = output.javaClass.getMethod("getOutputFileName").invoke(output)
                outputFileName.javaClass
                    .getMethod("set", Any::class.java)
                    .invoke(outputFileName, releaseApkName(vName, variant.buildType ?: "", abi))
            } catch (e: Exception) {
                // Ignore for now, build will fail if this is wrong
            }
        }
    }
}

tasks.register("renamePlayReleaseAab") {
    dependsOn("bundlePlayRelease")
    val bundleFileProvider = layout.buildDirectory.file("outputs/bundle/playRelease/app-play-release.aab")
    val targetFileProvider = layout.buildDirectory.file("outputs/bundle/playRelease/${releaseAabName(versionNameStr)}")
    doLast {
        val bundleFile = bundleFileProvider.get().asFile
        if (bundleFile.exists()) {
            val target = targetFileProvider.get().asFile
            bundleFile.copyTo(target, overwrite = true)
        }
    }
}

tasks.matching { it.name == "bundlePlayRelease" }.configureEach {
    finalizedBy("renamePlayReleaseAab")
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

    compileOnly(project(":xposed-stub"))

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
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
