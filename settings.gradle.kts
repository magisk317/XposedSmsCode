pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(
    ":app",
    ":runtime",
    ":core",
    ":smscode-core:xposed",
    ":smscode-core:hook",
    ":smscode-core:domain",
    ":smscode-core:runtime",
    ":smscode-core:contract",
    ":smscode-core:rule",
    ":smscode-core:verification",
    ":magisk-ui-kit",
)
