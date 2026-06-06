plugins {
    alias(libs.plugins.android.application)
    id("smscode.android.common")
    id("smscode.app.signing")
    id("smscode.app.packaging")
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.ksp)
    id(libs.plugins.kotlin.compose.get().pluginId)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

val versionNameStr = libs.versions.versionName.get()
val versionCodeInt = libs.versions.versionCode.get().toInt()
val minSdkStr = libs.versions.minSdk.get()
val targetSdkStr = libs.versions.targetSdk.get()
val ndkVersionStr = libs.versions.ndk.get()
val relayDownloadUrl = "https://github.com/magisk317/xinyi-relay"
val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false
val generatedSmsCodeRulesAssetsDir = layout.buildDirectory.dir("generated/smscodeRulesAssets")
val syncSmsCodeRulesAssets by tasks.registering(Sync::class) {
    val rulesRoot = rootProject.layout.projectDirectory.dir("smscode-rules")
    from(rulesRoot.dir("_meta")) {
        into("meta")
    }
    from(rulesRoot.dir("rules")) {
        into("rules")
    }
    into(generatedSmsCodeRulesAssetsDir.map { it.dir("smscode-rules") })
}

android {
    namespace = "com.github.tianma8023.xposed.smscode"
    ndkVersion = ndkVersionStr

    productFlavors {
        named("legacy") {
            proguardFile("proguard-legacy.pro")
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
        buildConfigField("boolean", "IS_LITE_BUILD", "true")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
        buildConfigField("String", "B_DOWNLOAD_URL", "\"$relayDownloadUrl\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(generatedSmsCodeRulesAssetsDir.get().asFile)
        }
    }
    packaging {
        resources {
            excludes += "**/*.kotlin_*"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            merges += "META-INF/xposed/*"
        }
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(
                runCatching {
                    org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString())
                }.getOrElse { org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25 }
            )
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        val flavors = variantBuilder.productFlavors.toMap()
        val enabledForLegacyBranch = flavors["distribution"] == "github" &&
            flavors["xposedApi"] == "legacy"
        if (!enabledForLegacyBranch) {
            variantBuilder.enable = false
        }
    }
}

tasks.named("preBuild") {
    dependsOn(syncSmsCodeRulesAssets)
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    implementation(project(":runtime"))
    implementation(project(":smscode-core:smscode-domain"))
    implementation(project(":smscode-core:smscode-runtime-common"))
    implementation(project(":smscode-core:smscode-verification-core"))
    implementation(project(":smscode-core:smscode-xposed-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    add("legacyCompileOnly", project(":xposed-stub"))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
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

val verifyNoLocalVerificationEngine by tasks.registering {
    group = "verification"
    description = "Ensure app does not reintroduce local verification engine infrastructure already shared in smscode-core."

    val bannedFiles = listOf(
        "src/main/java/com/github/magisk317/smscode/xp/hook/code/InboundSmsBlocker.kt",
        "src/main/java/com/github/magisk317/smscode/xp/hook/code/InboundSmsMethodInvoker.kt",
        "src/main/java/com/github/magisk317/smscode/xp/hook/code/SmsIntentHookSupport.kt",
    )
    val projectRoot = layout.projectDirectory.asFile

    inputs.files(bannedFiles.map { layout.projectDirectory.file(it) })

    doLast {
        val violations = bannedFiles.filter { projectRoot.resolve(it).exists() }
        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("App must not reintroduce local verification engine infrastructure:")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyNoLocalVerificationEngine)
}
