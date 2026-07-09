plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    alias(libs.plugins.kotlin.serialization)
    id("smscode.android.common")
}

val minSdkInt = libs.versions.minSdk.get().toInt()
val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false
val enableUiKitStyleSwitch = findProperty("enableUiKitStyleSwitch")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "com.github.magisk317.smscode.core"

    defaultConfig {
        minSdk = minSdkInt
        buildConfigField("int", "VERSION_CODE", libs.versions.versionCode.get())
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.versionName.get()}\"")
        buildConfigField("boolean", "IS_LITE_BUILD", "true")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
        buildConfigField("boolean", "ENABLE_UI_KIT_STYLE_SWITCH", enableUiKitStyleSwitch.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable.add("MissingTranslation")
        disable.add("LocalContextGetResourceValueCall")
    }
}

dependencies {
    implementation(project(":runtime"))
    api(project(":magisk-ui-kit"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.timber)
    implementation(libs.kotlinx.collections.immutable)
    add("playImplementation", libs.play.app.update)
    add("playImplementation", libs.billing.ktx)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val verifyNoRuntimeStorageImplLeak = tasks.register("verifyNoRuntimeStorageImplLeak") {
    group = "verification"
    description = "Ensure the core module does not directly depend on runtime storage/update implementation types."

    val sourceRoot = layout.projectDirectory.dir("src/main/java")
    val recordSourceRoot = layout.projectDirectory.dir("src/main/java/com/github/magisk317/smscode/ui/record")
    val projectRoot = layout.projectDirectory.asFile
    val bannedRegexes = listOf(
        Regex("""^\s*import\s+com\.github\.magisk317\.smscode\.data\.db\.(AppDatabase|DBManager|DBProvider)\b"""),
        Regex("""^\s*import\s+com\.github\.magisk317\.smscode\.data\.update\."""),
        Regex("""^\s*import\s+com\.github\.magisk317\.smscode\.common\.utils\.PrefsReader\b"""),
        Regex("""^\s*import\s+com\.github\.magisk317\.smscode\.common\.utils\.NotificationUtils\b"""),
        Regex("""\bcom\.github\.magisk317\.smscode\.feature\.backup\."""),
        Regex("""\bcom\.github\.magisk317\.smscode\.feature\.store\."""),
    )
    val recordBannedRegexes = listOf(
        Regex("""^\s*import\s+com\.github\.magisk317\.smscode\.runtime\.RuntimeStorageFacade\b"""),
        Regex("""\bRuntimeStorageFacade\.dbManager\("""),
        Regex("""^\s*import\s+io\.github\.magisk317\.smscode\.runtime\.common\.utils\.JsonUtils\b"""),
    )

    inputs.dir(sourceRoot)
    inputs.dir(recordSourceRoot)

    doLast {
        val violations = sourceRoot
            .asFileTree
            .matching { include("**/*.kt") }
            .files
            .flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    if (bannedRegexes.any { regex -> regex.containsMatchIn(line) }) {
                        "${source.relativeTo(projectRoot)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            } + recordSourceRoot
            .asFileTree
            .matching { include("**/*.kt") }
            .files
            .flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    if (recordBannedRegexes.any { regex -> regex.containsMatchIn(line) }) {
                        "${source.relativeTo(projectRoot)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Core must not directly depend on runtime storage/update implementation types or runtime feature internals:")
                    appendLine("Record UI must go through RuntimeCodeRecordFacade for storage and export operations:")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyNoRuntimeStorageImplLeak)
}
