plugins {
    id("magisk.android.library")
    id("kotlin-parcelize")
}

android {
    namespace = "com.github.magisk317.smscode.hook"
    buildFeatures.buildConfig = true

    defaultConfig {
        missingDimensionStrategy("distribution", "github")

        // These mirror :app's build config values needed by hook code.
        // They are set here so the :hook module compiles independently.
        buildConfigField("String", "APPLICATION_ID", "\"com.github.tianma8023.xposed.smscode\"")
        buildConfigField("String", "VERSION_NAME", "\"${findProperty("appVersionName") ?: "dev"}\"")
        buildConfigField("int", "VERSION_CODE", "${findProperty("appVersionCode") ?: 1}")
        buildConfigField("int", "MODULE_VERSION", "${findProperty("moduleVersion") ?: 1}")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", "${findProperty("allowConflictBypass") ?: false}")
    }
}

dependencies {
    implementation(project(":runtime"))
    implementation(project(":core"))
    implementation(project(":smscode-core:hook"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:contract"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":magisk-xposed-kit"))
    implementation(libs.androidx.core.ktx)
    compileOnly(libs.libxposed.api)
}
