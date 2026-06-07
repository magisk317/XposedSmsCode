import dev.detekt.gradle.extensions.DetektExtension
import com.adarshr.gradle.testlogger.theme.ThemeType
import org.gradle.api.tasks.Exec
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    configurations.all {
        resolutionStrategy {
            force("org.ow2.asm:asm:9.10")
            force("org.ow2.asm:asm-commons:9.10")
            force("org.ow2.asm:asm-tree:9.10")
            force("org.ow2.asm:asm-util:9.10")
            force("org.ow2.asm:asm-analysis:9.10")
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("io.netty:netty-codec:4.1.133.Final")
            force("io.netty:netty-codec-http:4.1.133.Final")
            force("io.netty:netty-codec-http2:4.1.133.Final")
            force("io.netty:netty-common:4.1.118.Final")
            force("io.netty:netty-handler:4.1.118.Final")
            force("io.netty:netty-handler-proxy:4.1.133.Final")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)
        }
    }
}

plugins {
    id("nl.littlerobots.version-catalog-update") version "1.1.0"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.test.logger) apply false
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

fun KoverProjectExtension.configureProjectKoverVerification() {
    reports {
        verify {
            rule {
                // Start with a pragmatic threshold and tighten later.
                minBound(60)
            }
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
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
    }

    if (enableKover) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        extensions.configure<KoverProjectExtension>("kover") {
            configureProjectKoverVerification()
        }
    }

    pluginManager.withPlugin("com.android.application") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("com.android.library") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }

    // Configure test-logger for all projects
    plugins.withId("com.adarshr.test-logger") {
        configure<com.adarshr.gradle.testlogger.TestLoggerExtension> {
            theme = ThemeType.MOCHA
            showExceptions = true
            showStackTraces = true
            showCauses = true
            showSummary = true
        }
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }

    configurations.all {
        resolutionStrategy {
            force(catalog.apache.httpclient)
            force("org.ow2.asm:asm:9.10")
            force("org.ow2.asm:asm-commons:9.10")
            force("org.ow2.asm:asm-tree:9.10")
            force("org.ow2.asm:asm-util:9.10")
            force("org.ow2.asm:asm-analysis:9.10")
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:$forcedKotlinVersion")
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("io.netty:netty-codec:4.1.133.Final")
            force("io.netty:netty-codec-http:4.1.133.Final")
            force("io.netty:netty-codec-http2:4.1.133.Final")
            force("io.netty:netty-common:4.1.118.Final")
            force("io.netty:netty-handler:4.1.118.Final")
            force("io.netty:netty-handler-proxy:4.1.133.Final")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Maintenance task now automatically hooked via magisk.maintenance plugin
