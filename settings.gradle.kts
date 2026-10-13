import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io") {
            name = "JitPack"
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
        maven {
            name = "MagiskMobilePrivate"
            url = uri(
                providers.gradleProperty("mobile.private.maven.url").orNull
                    ?: System.getenv("MOBILE_PRIVATE_MAVEN_URL")
                    ?: "https://gitlab.com/api/v4/projects/85187820/packages/maven",
            )
            content {
                includeGroup("com.magisk317.mobile")
            }
        }
    }
}

include(
    ":app",
    ":hook",
    ":runtime",
    ":core",
    ":smscode-core:hook",
    ":smscode-core:domain",
    ":smscode-core:runtime",
    ":smscode-core:contract",
    ":smscode-core:rule",
    ":smscode-core:verification",
    ":smscode-core:db",
    ":magisk-ui-kit",
    ":magisk-ui-kit:billing",
    ":magisk-xposed-kit",
    ":magisk-xposed-kit:logging",
    ":magisk-xposed-kit:diagnostics",
    ":magisk-xposed-kit:permission",
    ":magisk-xposed-kit:entitlement",
)

project(":smscode-core:hook").projectDir = file("smscode/core/hook")
project(":smscode-core:domain").projectDir = file("smscode/core/domain")
project(":smscode-core:runtime").projectDir = file("smscode/core/runtime")
project(":smscode-core:contract").projectDir = file("smscode/core/contract")
project(":smscode-core:rule").projectDir = file("smscode/core/rule")
project(":smscode-core:verification").projectDir = file("smscode/core/verification")
project(":smscode-core:db").projectDir = file("smscode/core/db")
project(":smscode-core").projectDir = file("smscode/core")
project(":magisk-xposed-kit:logging").projectDir = file("magisk-xposed-kit/logging")
project(":magisk-xposed-kit:diagnostics").projectDir = file("magisk-xposed-kit/diagnostics")
project(":magisk-xposed-kit:permission").projectDir = file("magisk-xposed-kit/permission")
project(":magisk-xposed-kit:entitlement").projectDir = file("magisk-xposed-kit/entitlement")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")
