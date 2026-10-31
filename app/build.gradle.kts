plugins {
    id("magisk.android.application")
    id("magisk.android.distribution")
    id("magisk.app.signing")
    id("magisk.app.packaging")
    id("magisk.mobile.gate")
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("magisk.android.room")
    id("magisk.android.compose")
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

val versionNameStr = providers.gradleProperty("versionName")
    .orElse(libs.versions.versionName)
    .get()
val versionCodeInt = providers.gradleProperty("versionCode")
    .map { requireNotNull(it.toIntOrNull()) { "Invalid -PversionCode=$it" } }
    .orElse(libs.versions.versionCode.map { it.toInt() })
    .get()
val ndkVersionStr = libs.versions.ndk.get()
val relayDownloadUrl = "https://github.com/magisk3171/xinyi-relay"
val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false
val mobileEntitlementEnforced = true
val mobileEntitlementApiOrigin = findProperty("mobileEntitlementApiOrigin")?.toString()
    ?: "https://activate.magisk317.qzz.io"
val mobileEntitlementSigningPublicJwk = findProperty("mobileEntitlementSigningPublicJwk")?.toString()
    ?: """{"kty":"EC","x":"4kPpwUt1wFRuF3EqGq6q57J3YmANf7wyiNH90FNkAbI","y":"U4-E1XK6LjWIXMFNEoSAoik7nD1S07BDb7qAipQd4Ts","crv":"P-256","alg":"ES256","use":"sig","kid":"mobile-entitlement-1"}"""
fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
val generatedSmsCodeRulesAssetsDir = layout.buildDirectory.dir("generated/smscodeRulesAssets")
val syncSmsCodeRulesAssets = tasks.register<Sync>("syncSmsCodeRulesAssets") {
    val rulesRoot = rootProject.layout.projectDirectory.dir("smscode/rules")
    from(rulesRoot.dir("_meta")) {
        into("meta")
    }
    from(rulesRoot.dir("rules")) {
        into("rules")
    }
    into(generatedSmsCodeRulesAssetsDir.map { it.dir("smscode-rules") })
}

val verifySmsCodeRulesIndex = tasks.register<org.gradle.api.tasks.Exec>("verifySmsCodeRulesIndex") {
    group = "verification"
    inputs.file(rootProject.layout.projectDirectory.file("smscode/rules/_meta/rules-index.json"))
    workingDir(rootProject.layout.projectDirectory.dir("smscode/rules").asFile)
    commandLine("python3", "tools/build_index.py", "--check")
}

android {
    namespace = "com.github.tianma8023.xposed.smscode"
    ndkVersion = ndkVersionStr


    androidResources {
        localeFilters.addAll(listOf("en", "zh-rCN", "zh-rTW"))
    }

    defaultConfig {
        applicationId = "com.github.tianma8023.xposed.smscode"
        versionCode = versionCodeInt
        versionName = versionNameStr

        buildConfigField("String", "LOG_TAG", "\"smscode\"")
        buildConfigField("int", "MODULE_VERSION", "$versionCodeInt")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
        buildConfigField("String", "B_DOWNLOAD_URL", "\"$relayDownloadUrl\"")
        buildConfigField("boolean", "MOBILE_ENTITLEMENT_ENFORCED", mobileEntitlementEnforced.toString())
        buildConfigField("String", "MOBILE_ENTITLEMENT_API_ORIGIN", buildConfigString(mobileEntitlementApiOrigin))
        buildConfigField("String", "MOBILE_ENTITLEMENT_SIGNING_PUBLIC_JWK", buildConfigString(mobileEntitlementSigningPublicJwk))
    }

    // MOBILE_ENTITLEMENT_CHANNEL and ENABLE_MOBILE_ENTITLEMENT come from the
    // magisk.mobile.gate plugin so the app and the library modules the hook
    // process runs from cannot drift apart.

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(generatedSmsCodeRulesAssetsDir.get().asFile.absolutePath)
        }
        // Sources live under src/<name>/kotlin. AGP compiles src/<name>/java by default, so
        // each source set is pointed at the kotlin directory explicitly.
        listOf("main", "test", "github", "play").forEach { name ->
            findByName(name)?.kotlin?.directories?.add("src/$name/kotlin")
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

}

tasks.named("preBuild") {
    dependsOn(syncSmsCodeRulesAssets, verifySmsCodeRulesIndex)
}

tasks.named("check") {
    dependsOn(verifySmsCodeRulesIndex)
}

dependencies {
    implementation(libs.mobile.entitlement.android)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":presentation"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":magisk-ui-kit:update"))
    implementation(project(":hook"))
    implementation(project(":runtime"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:db"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":smscode-core:hook"))
    implementation(project(":magisk-xposed-kit"))
    implementation(project(":magisk-xposed-kit:capability"))
    implementation(project(":magisk-xposed-kit:runtime"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    compileOnly(libs.libxposed.api)
    implementation(project(":magisk-xposed-kit:service"))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
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

val verifyNoLocalVerificationEngine = tasks.register("verifyNoLocalVerificationEngine") {
    group = "verification"
    description = "Ensure the app shell owns neither hook runtime code nor a local verification engine."

    val projectRoot = layout.projectDirectory.asFile
    val sourceRoots = listOf("src/main/kotlin", "src/main/java").map(projectRoot::resolve)
    val forbiddenPackagePath = "/io/github/magisk317/smscode/xp/"

    inputs.files(sourceRoots)

    doLast {
        val violations = sourceRoots.flatMap { root ->
            if (!root.exists()) return@flatMap emptyList()
            root.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .filter { it.path.replace(File.separatorChar, '/').contains(forbiddenPackagePath) }
                .map { it.relativeTo(projectRoot).path }
                .toList()
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("App shell must not own Xposed runtime or local verification engine code; use :hook or smscode-core:")
                    violations.distinct().sorted().forEach { appendLine(it) }
                },
            )
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyNoLocalVerificationEngine)
}
