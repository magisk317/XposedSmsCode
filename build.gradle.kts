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
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("io.netty:netty-codec:5.0.0.Alpha2")
            force("io.netty:netty-codec-http:5.0.0.Alpha2")
            force("io.netty:netty-codec-http2:5.0.0.Alpha2")
            force("io.netty:netty-common:5.0.0.Alpha2")
            force("io.netty:netty-handler:5.0.0.Alpha2")
            force("io.netty:netty-handler-proxy:5.0.0.Alpha2")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)

            // Java 27 bytecode target: AGP 9.4.1 bundles ASM 9.9 (V26 max) and
            // rejects major 71. ASM 9.10.1 adds V27; force the family here because
            // this is the classpath AGP actually runs on (project-level forces do
            // not reach the plugin classpath). Deliberately outside the managed
            // block: the dependency-force workflow rewrites that block wholesale
            // and would drop the comment on its next run.
            force("org.ow2.asm:asm:9.10.1")
            force("org.ow2.asm:asm-analysis:9.10.1")
            force("org.ow2.asm:asm-commons:9.10.1")
            force("org.ow2.asm:asm-tree:9.10.1")
            force("org.ow2.asm:asm-util:9.10.1")
        }
    }
}

plugins {
    id("nl.littlerobots.version-catalog-update") version "1.1.0"
    id("magisk.android.application") apply false
    id("magisk.android.library") apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    id("magisk.android.compose") apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    id("magisk.maintenance")
}

val catalog = libs
val forcedKotlinVersion = libs.versions.kotlin.get()
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
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        configureDetekt()
    }

}

allprojects {
    configurations.configureEach {
        resolutionStrategy {
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("io.netty:netty-codec:5.0.0.Alpha2")
            force("io.netty:netty-codec-http:5.0.0.Alpha2")
            force("io.netty:netty-codec-http2:5.0.0.Alpha2")
            force("io.netty:netty-common:5.0.0.Alpha2")
            force("io.netty:netty-handler:5.0.0.Alpha2")
            force("io.netty:netty-handler-proxy:5.0.0.Alpha2")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)

            // Custom migration overrides for Java 26 compatibility
            force(catalog.apache.httpclient)
            force("org.ow2.asm:asm:9.10.1")
            force("org.ow2.asm:asm-commons:9.10.1")
            force("org.ow2.asm:asm-tree:9.10.1")
            force("org.ow2.asm:asm-util:9.10.1")
            force("org.ow2.asm:asm-analysis:9.10.1")
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:$forcedKotlinVersion")
        }
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
)
val forbiddenMainModuleDependencies = buildMap {
    put(":runtime", setOf(":app", ":core", ":hook"))
    put(":hook", setOf(":app", ":core"))
    put(":core", setOf(":app", ":hook"))
    sharedCoreProjectPaths.forEach { sourcePath ->
        put(sourcePath, setOf(":app", ":core", ":runtime", ":magisk-ui-kit"))
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
    description = "Run root module-boundary checks for app/core/runtime and shared smscode-core."

    dependsOn(
        verifyMainModuleDependencies,
        ":app:verifyNoLocalVerificationEngine",
        ":core:verifyNoRuntimeStorageImplLeak",
        ":runtime:verifyNoComposeUiLeak",
    )
}

tasks.register("check") {
    group = "verification"
    description = "Run root project verification checks."
    dependsOn(verifyModuleBoundaries)
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Maintenance task now automatically hooked via magisk.maintenance plugin
