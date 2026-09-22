import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.authentication.http.HttpHeaderAuthentication

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
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven {
            name = "MagiskMobilePrivate"
            url = uri(
                providers.gradleProperty("mobile.private.maven.url").orNull
                    ?: System.getenv("MOBILE_PRIVATE_MAVEN_URL")
                    ?: "https://gitlab.com/api/v4/projects/85187820/packages/maven",
            )
            val jobToken = System.getenv("CI_JOB_TOKEN")
            val deployToken = System.getenv("GITLAB_DEPLOY_TOKEN")
            val privateToken = System.getenv("GITLAB_TOKEN")
            if (!jobToken.isNullOrBlank()) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = jobToken
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            } else if (!deployToken.isNullOrBlank()) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Deploy-Token"
                    value = deployToken
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            } else if (!privateToken.isNullOrBlank()) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Private-Token"
                    value = privateToken
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
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
    ":magisk-ui-kit",
    ":magisk-ui-kit:billing",
    ":magisk-xposed-kit",
    ":magisk-xposed-kit:logging",
    ":magisk-xposed-kit:diagnostics",
    ":magisk-xposed-kit:permission",
)

project(":smscode-core:hook").projectDir = file("smscode/core/hook")
project(":smscode-core:domain").projectDir = file("smscode/core/domain")
project(":smscode-core:runtime").projectDir = file("smscode/core/runtime")
project(":smscode-core:contract").projectDir = file("smscode/core/contract")
project(":smscode-core:rule").projectDir = file("smscode/core/rule")
project(":smscode-core:verification").projectDir = file("smscode/core/verification")
project(":smscode-core").projectDir = file("smscode/core")
project(":magisk-xposed-kit:logging").projectDir = file("magisk-xposed-kit/logging")
project(":magisk-xposed-kit:diagnostics").projectDir = file("magisk-xposed-kit/diagnostics")
project(":magisk-xposed-kit:permission").projectDir = file("magisk-xposed-kit/permission")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")
