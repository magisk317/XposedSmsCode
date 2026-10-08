plugins {
    id("magisk.android.library")
    id("kotlin-parcelize")
}

val appVersionName = libs.versions.versionName.get()
val appVersionCode = libs.versions.versionCode.get().toInt()
val gitCommitHash = providers.exec {
    commandLine("git", "-C", projectDir, "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim().ifEmpty { "unknown" }

android {
    namespace = "io.github.magisk317.smscode.hook"
    buildFeatures.buildConfig = true

    defaultConfig {
        missingDimensionStrategy("distribution", "github")

        // Keep hook diagnostics and module compatibility checks on the app's catalog version.
        buildConfigField("String", "LOG_TAG", "\"smscode\"")
        buildConfigField("String", "COMMIT_HASH", "\"$gitCommitHash\"")
        buildConfigField("String", "APPLICATION_ID", "\"com.github.tianma8023.xposed.smscode\"")
        buildConfigField("String", "VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("int", "VERSION_CODE", "$appVersionCode")
        buildConfigField("int", "MODULE_VERSION", "$appVersionCode")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", "${findProperty("allowConflictBypass") ?: false}")
    }
    // Sources live under src/<name>/kotlin. AGP compiles src/<name>/java by default, so
    // each source set is pointed at the kotlin directory explicitly.
    sourceSets {
        listOf("main", "test", "github", "fdroid", "play", "nonPlayBilling").forEach { name ->
            findByName(name)?.kotlin?.directories?.add("src/$name/kotlin")
        }
    }
}

dependencies {
    implementation(project(":runtime"))
    implementation(project(":smscode-core:hook"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:rule"))
    implementation(project(":smscode-core:contract"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:db"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":magisk-xposed-kit"))
    implementation(libs.androidx.core.ktx)
    compileOnly(libs.libxposed.api)
}
