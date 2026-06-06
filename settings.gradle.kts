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
    ":smscode-core:smscode-xposed-core",
    ":smscode-core:smscode-hook-core",
    ":smscode-core:smscode-domain",
    ":smscode-core:smscode-runtime-common",
    ":smscode-core:smscode-runtime-contract",
    ":smscode-core:smscode-rule-core",
    ":smscode-core:smscode-verification-core",
    ":magisk-ui-kit",
)
