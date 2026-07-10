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
    ":hook",
    ":runtime",
    ":core",
    ":smscode-core:hook",
    ":smscode-core:domain",
    ":smscode-core:runtime",
    ":smscode-core:contract",
    ":smscode-core:rule",
    ":smscode-core:verification",
    ":magisk-ui-kit",
    ":magisk-xposed-kit",
)

project(":smscode-core:hook").projectDir = file("smscode/core/hook")
project(":smscode-core:domain").projectDir = file("smscode/core/domain")
project(":smscode-core:runtime").projectDir = file("smscode/core/runtime")
project(":smscode-core:contract").projectDir = file("smscode/core/contract")
project(":smscode-core:rule").projectDir = file("smscode/core/rule")
project(":smscode-core:verification").projectDir = file("smscode/core/verification")
project(":smscode-core").projectDir = file("smscode/core")
