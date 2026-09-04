plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // Build-time-only Ink compiler used by the root `compileInk` task. The version MUST be kept in
    // sync with `bladeInk` in gradle/libs.versions.toml (the version catalog isn't visible from the
    // buildSrc build, so the coordinate is spelled out here). blade-ink-compiler pulls the matching
    // blade-ink runtime transitively (compile() returns a runtime Story we call toJson() on).
    implementation("com.bladecoder.ink:blade-ink-compiler:1.3.2")
    // The compiler returns a runtime Story (we call toJson() on it) and exposes Error.ErrorHandler,
    // but does not pull the runtime transitively — declare it explicitly.
    implementation("com.bladecoder.ink:blade-ink:1.3.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
