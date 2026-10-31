import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Exec

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    configurations.all {
        resolutionStrategy {
            // Java 27 bytecode target: AGP 9.4.1 bundles ASM 9.9 (V26 max) and
            // rejects major 71. ASM 9.10.1 adds V27; force the family here because
            // this is the classpath AGP actually runs on (project-level forces do
            // not reach the plugin classpath). Deliberately outside the managed
            // block: the dependency-force workflow rewrites that block wholesale
            // and would drop the comment on its next run.
            force("org.ow2.asm:asm:9.11.1")
            force("org.ow2.asm:asm-analysis:9.11.1")
            force("org.ow2.asm:asm-commons:9.11.1")
            force("org.ow2.asm:asm-tree:9.11.1")
            force("org.ow2.asm:asm-util:9.11.1")
        }
    }
}


// Java 27 bytecode target, lint side: AGP 9.5.0-rc01's lint-api (32.5.0-rc01)
// resolves ASM 9.9 (V26 max) on androidLintTool, so lintAnalyze skips every
// major-71 class with "broken class file?" warnings while the job stays green.
// The buildscript force above does not reach project configurations; force the
// same family here until the lint toolchain ships ASM >= 9.10 itself.
allprojects {
    configurations.configureEach {
        if (name == "androidLintTool") {
            resolutionStrategy {
                force("org.ow2.asm:asm:9.11.1")
                force("org.ow2.asm:asm-analysis:9.11.1")
                force("org.ow2.asm:asm-commons:9.11.1")
                force("org.ow2.asm:asm-tree:9.11.1")
                force("org.ow2.asm:asm-util:9.11.1")
            }
        }
    }
}
plugins {
    id("nl.littlerobots.version-catalog-update") version "1.1.2"
    id("magisk.android.application") apply false
    id("magisk.android.library") apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    // Pin the KMP plugin versions for the composite build: magisk-ui-kit
    // requests org.jetbrains.kotlin.multiplatform with the shared catalog
    // version, but build-logic pulls kotlin-gradle-plugin onto the
    // composite classpath without a version descriptor - Gradle then
    // refuses the versioned request ("already on the classpath with an
    // unknown version"). Declaring both markers here, apply false, is the
    // same pinning MiPushFramework uses.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    id("magisk.android.compose") apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    id("magisk.maintenance")
    id("magisk.distribution.policy")
}

// Which distributions this app ships as and what each one compiles in. This is
// product data, so it belongs here rather than in the shared convention: the
// convention owns only the mechanism that turns this table into AGP flavors.
//
// Play and GitHub are the active channels. F-Droid is paused - the commented
// flavor in each channel block is the single resume point, so a paused
// distribution stays a documented state instead of becoming a flavor that was
// quietly dropped.
magiskDistribution {
    flavor("play") {
        buildConfigBoolean("ENABLE_SMS_CHANNEL", false)
        buildConfigBoolean("ALLOW_HTTP_WEBHOOK", true)
        buildConfigBoolean("ENABLE_ACCESSIBILITY_AUTO_INPUT", false)
        buildConfigBoolean("HAS_BILLING", true)
    }
    flavor("github") {
        buildConfigBoolean("ENABLE_SMS_CHANNEL", true)
        buildConfigBoolean("ALLOW_HTTP_WEBHOOK", true)
        buildConfigBoolean("ENABLE_ACCESSIBILITY_AUTO_INPUT", true)
        buildConfigBoolean("HAS_BILLING", false)
    }
    // F-Droid paused: re-enable the flavor above and add it here to restore it.
    // flavor("fdroid") { ... }
}


val catalog = libs
val enableKover = providers.gradleProperty("enableKover")
    .map { it.toBooleanStrictOrNull() ?: false }
    .getOrElse(false) ||
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("kover", ignoreCase = true)
    }

subprojects {
    fun Project.configureDetekt() {
        apply(plugin = "dev.detekt")
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            autoCorrect = true
            parallel = true
            buildUponDefaultConfig = false
            config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        }
        dependencies {
            "detektPlugins"(catalog.detekt.rules.ktlint)
        }
        // detekt CLI whitelists JVM targets and 2.0.0-alpha.6 caps at 26, so the
        // analysis target must not exceed that ceiling even though we emit Java 27
        // bytecode (the compile target tracks the Gradle daemon JVM, which can be
        // newer than the bytecode we emit). Revisit when detekt ships V27 support.
        tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
            jvmTarget.set(minOf(catalog.versions.javaBytecode.get().toInt(), 26).toString())
        }
        tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
            jvmTarget.set(minOf(catalog.versions.javaBytecode.get().toInt(), 26).toString())
        }
    }

    if (enableKover) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }

    pluginManager.withPlugin("com.android.application") {
        configureDetekt()
    }
    pluginManager.withPlugin("com.android.library") {
        configureDetekt()
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configureDetekt()
    }
    // Multiplatform modules (the kit's logging/keepalive, magisk-ui-kit and the
    // desktop modules) matched none of the hooks above, so they got no detekt task at all and
    // their findings were never computed. Same treatment as the Android modules: analysed, and
    // non-blocking until each one is measured.
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        configureDetekt()
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        configureDetekt()
    }

}

val mainModuleDependencyViolations = objects.listProperty(String::class.java)

val sharedCoreProjectPaths = listOf(
    ":smscode-core:contract",
    ":smscode-core:domain",
    ":smscode-core:hook",
    ":smscode-core:rule",
    ":smscode-core:runtime",
    ":smscode-core:verification",
    ":smscode-core:db",
)
val forbiddenMainModuleDependencies = buildMap {
    put(":runtime", setOf(":app", ":presentation", ":hook"))
    put(":hook", setOf(":app", ":presentation"))
    put(":presentation", setOf(":app", ":hook"))
    sharedCoreProjectPaths.forEach { sourcePath ->
        put(sourcePath, setOf(":app", ":presentation", ":runtime", ":magisk-ui-kit"))
    }
}

gradle.projectsEvaluated {
    val violations = forbiddenMainModuleDependencies.flatMap { (sourcePath, forbiddenTargets) ->
        val sourceProject = project(sourcePath)
        sourceProject.configurations.flatMap { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java)
                .filter { dependency -> dependency.path in forbiddenTargets }
                .map { dependency ->
                    "${sourceProject.path}:${configuration.name} -> ${dependency.path}"
                }
        }
    }.distinct().sorted()

    mainModuleDependencyViolations.set(violations)
}

val verifyMainModuleDependencies = tasks.register("verifyMainModuleDependencies") {
    group = "verification"
    description = "Ensure main and shared modules keep the intended Gradle dependency direction."

    val violationsInput = mainModuleDependencyViolations
    inputs.property("violations", violationsInput)

    doLast {
        val violations = violationsInput.get()
        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Module dependency boundary violations:")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

val verifyModuleBoundaries = tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Run root module-boundary checks for app/presentation/runtime and shared smscode-core."

    dependsOn(
        verifyMainModuleDependencies,
        ":app:verifyNoLocalVerificationEngine",
        ":presentation:verifyNoRuntimeStorageImplLeak",
        ":runtime:verifyNoComposeUiLeak",
    )
}

tasks.register<Exec>("verifyNoGlobalWarningMute") {
    group = "verification"
    description = "Ensure the Kotlin discarded-result diagnostics stay enabled; tolerated failures must be explicit at the call site."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/verify_no_global_warning_mute.sh")
}

tasks.register<Exec>("reportGodFiles") {
    group = "verification"
    description = "Report large production Kotlin files outside shared-submodule roots."
    commandLine("bash", "scripts/checks/report_god_files.sh", "--report")
}

tasks.register<Exec>("verifyGodFileLimits") {
    group = "verification"
    description = "Fail when a production Kotlin god file is added, grows, or diverges from its reviewed limit."
    commandLine("bash", "scripts/checks/report_god_files.sh", "--verify")
}

tasks.register("check") {
    group = "verification"
    description = "Run root project verification checks."
    dependsOn(verifyModuleBoundaries, "verifyNoGlobalWarningMute", "verifyGodFileLimits")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Maintenance task now automatically hooked via magisk.maintenance plugin


