pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// foojay-resolver-convention is applied to settings; settings scripts can't read the
// version catalog before plugin resolution, so this literal mirrors `foojayResolver`
// in gradle/libs.versions.toml. Keep the two in sync.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("lwjgl3", "android", "core")
