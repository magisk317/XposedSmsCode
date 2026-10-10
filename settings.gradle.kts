import java.io.File
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.authentication.http.HttpHeaderAuthentication

/**
 * Detect which auth header the given GitLab token validates under.
 * Classic PATs / project tokens / OAuth tokens all work with `Authorization: Bearer`,
 * while some legacy setups only accept `Private-Token`.
 */
fun resolveGitlabAuthHeader(token: String): Pair<String, String> {
    val cacheFile = File(System.getProperty("user.home"), ".gradle/gitlab_auth_header_cache")
    val key = token.hashCode().toString()
    runCatching {
        if (cacheFile.exists()) {
            cacheFile.readLines()
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.let { name ->
                    val value = if (name == "Authorization") "Bearer $token" else token
                    return name to value
                }
        }
    }
    val probeUrl = "https://gitlab.com/api/v4/projects/85187820/packages/maven/" +
        "com/magisk317/mobile/entitlement-android/0.3.0/entitlement-android-0.3.0.pom"
    val candidates = listOf("Authorization" to "Bearer $token", "Private-Token" to token)
    var chosen: Pair<String, String>? = null
    for ((name, value) in candidates) {
        val ok = runCatching {
            (java.net.URL(probeUrl).openConnection() as java.net.HttpURLConnection).let { conn ->
                conn.requestMethod = "HEAD"
                conn.setRequestProperty(name, value)
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val code = conn.responseCode
                conn.disconnect()
                code != 401
            }
        }.getOrElse { false }
        if (ok) { chosen = name to value; break }
    }
    val result = chosen ?: ("Authorization" to "Bearer $token")
    runCatching {
        cacheFile.parentFile?.mkdirs()
        val kept = runCatching { cacheFile.readLines() }.getOrElse { emptyList() }
            .filter { !it.startsWith("$key=") } + listOf("$key=${result.first}")
        cacheFile.writeText(kept.takeLast(20).joinToString("\n"))
    }
    return result
}

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
            val jobToken = System.getenv("CI_JOB_TOKEN")
            val deployToken = System.getenv("GITLAB_DEPLOY_TOKEN")
            val privateToken = System.getenv("GITLAB_TOKEN")
                ?: System.getenv("GITLAB_PRIVATE_TOKEN")
                ?: providers.gradleProperty("gitlab.token").orNull
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
                val (headerName, headerValue) = resolveGitlabAuthHeader(privateToken)
                credentials(HttpHeaderCredentials::class) {
                    name = headerName
                    value = headerValue
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
    ":smscode-core:db",
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
project(":smscode-core:db").projectDir = file("smscode/core/db")
project(":smscode-core").projectDir = file("smscode/core")
project(":magisk-xposed-kit:logging").projectDir = file("magisk-xposed-kit/logging")
project(":magisk-xposed-kit:diagnostics").projectDir = file("magisk-xposed-kit/diagnostics")
project(":magisk-xposed-kit:permission").projectDir = file("magisk-xposed-kit/permission")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")
