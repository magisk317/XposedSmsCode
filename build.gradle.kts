import dev.detekt.gradle.extensions.DetektExtension
import com.adarshr.gradle.testlogger.theme.ThemeType

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    configurations.all {
        resolutionStrategy {
            force(libs.jose4j)
            force(libs.jdom2)
            force(libs.apache.commons.lang3)
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
    alias(libs.plugins.kover)
    alias(libs.plugins.test.logger) apply false
}

kover {
    reports {
        verify {
            rule {
                // Start with a pragmatic threshold and tighten later.
                minBound(60)
            }
        }
    }
}

val catalog = libs

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
    }

    // Apply kover to all projects
    apply(plugin = "org.jetbrains.kotlinx.kover")

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
            force(catalog.jose4j)
            force(catalog.jdom2)
            force(catalog.apache.commons.lang3)
            force(catalog.apache.httpclient)
            force(catalog.netty.codec.http)
            force(catalog.netty.codec)
            force(catalog.netty.codec.http2)
            force(catalog.netty.common)
            force(catalog.netty.handler)
            force(catalog.netty.resolver)
            force(catalog.netty.transport)
            force(catalog.netty.buffer)
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.named<Wrapper>("wrapper") {
    val gradlewFile = layout.projectDirectory.file("gradlew")
    doLast {
        val file = gradlewFile.asFile
        if (file.exists()) {
            val content = file.readText()
            val cleanupScript = """
# Cleanup old Gradle caches
if [ -d "${"$"}APP_HOME/.gradle" ]; then
    (
        cd "${"$"}APP_HOME/.gradle" || exit
        # Find all version-like directories starting with a digit
        versions=$(ls -d [0-9]* 2>/dev/null)
        if [ -n "${"$"}versions" ]; then
            # Sort versions and keep the last one (latest)
            # Standard sort works fine for timestamped versions
            latest=$(echo "${"$"}versions" | sort | tail -n 1)

            # Iterate and remove non-latest versions
            for d in ${"$"}versions; do
                if [ "${"$"}d" != "${"$"}latest" ]; then
                    echo "Cleaning up old Gradle cache: ${"$"}d"
                    rm -rf "${"$"}d"
                fi
            done
        fi
    )
fi

"""
            if (!content.contains("Cleaning up old Gradle cache")) {
                val execCommand = "exec \"\$JAVACMD\" \"\$@\""
                if (content.contains(execCommand)) {
                    val replacement = """
"${"$"}JAVACMD" "${"$"}@"
EXIT_CODE=${"$"}?

$cleanupScript
exit ${"$"}EXIT_CODE
"""
                    val finalContent = content.replace(execCommand, replacement.trim())
                    file.writeText(finalContent)
                    println("Injected cleanup script into gradlew")
                }
            }
        }
    }
}
