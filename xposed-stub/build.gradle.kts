plugins {
    `java-library`
}

val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}
