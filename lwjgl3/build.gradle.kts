import io.github.fourlastor.construo.Target
import java.util.Locale

val appName: String by extra
val enableGraalNative: String by project
val projectVersion = project.version.toString()

buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        if (project.findProperty("enableGraalNative") == "true") {
            classpath(libs.graalvm.native.gradle.plugin)
        }
    }
}

plugins {
    application
    alias(libs.plugins.construo)
}

sourceSets["main"].resources.srcDir(rootProject.file("assets"))

application {
    mainClass.set("com.prpg.lwjgl3.Lwjgl3Launcher")
    applicationName = appName
}

eclipse.project.name = "$appName-lwjgl3"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
if (JavaVersion.current().isJava9Compatible) {
    tasks.named<JavaCompile>("compileJava") { options.release.set(21) }
}

dependencies {
    implementation(libs.gdx.backend.lwjgl3)
    implementation(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
    implementation(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    implementation(project(":core"))

    if (enableGraalNative == "true") {
        implementation(libs.gdx.svmhelper.backend.lwjgl3)
        implementation(libs.gdx.svmhelper.extension.freetype)
    }

    // Forces LWJGL3 to use at least the catalog-pinned version, currently 3.4.1, to avoid problems on Java 25 and up.
    constraints {
        implementation(libs.lwjgl)
        implementation(libs.lwjgl.glfw)
        implementation(libs.lwjgl.jemalloc)
        implementation(libs.lwjgl.openal)
        implementation(libs.lwjgl.opengl)
        implementation(libs.lwjgl.stb)
    }
}

val os = System.getProperty("os.name").lowercase(Locale.ROOT)

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    // You can uncomment the next line if your IDE claims a build failure even when the app closed properly.
    // isIgnoreExitValue = true
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.named<Jar>("jar") {
    archiveFileName.set("$appName-$projectVersion.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    exclude(
        "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
        "META-INF/maven/**"
    )
    manifest {
        attributes(
            mapOf(
                "Main-Class" to application.mainClass.get(),
                "Enable-Native-Access" to "ALL-UNNAMED",
                "Multi-Release" to "true"
            )
        )
    }
    doLast {
        archiveFile.get().asFile.setExecutable(true, false)
    }
}

// Builds a JAR that only includes the files needed to run on macOS.
tasks.register("jarMac") {
    dependsOn("jar")
    group = "build"
    val jar = tasks.getByName<Jar>("jar")
    jar.archiveFileName.set("$appName-$projectVersion-mac.jar")
    jar.exclude(
        "windows/x86/**", "windows/x64/**", "linux/arm32/**", "linux/arm64/**", "linux/x64/**",
        "**/*.dll", "**/*.so",
        "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**"
    )
}

// Builds a JAR that only includes the files needed to run on Linux.
tasks.register("jarLinux") {
    dependsOn("jar")
    group = "build"
    val jar = tasks.getByName<Jar>("jar")
    jar.archiveFileName.set("$appName-$projectVersion-linux.jar")
    jar.exclude(
        "windows/x86/**", "windows/x64/**", "macos/arm64/**", "macos/x64/**",
        "**/*.dll", "**/*.dylib",
        "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**"
    )
}

// Builds a JAR that only includes the files needed to run on Windows.
tasks.register("jarWin") {
    dependsOn("jar")
    group = "build"
    val jar = tasks.getByName<Jar>("jar")
    jar.archiveFileName.set("$appName-$projectVersion-win.jar")
    jar.exclude(
        "macos/arm64/**", "macos/x64/**", "linux/arm32/**", "linux/arm64/**", "linux/x64/**",
        "**/*.dylib", "**/*.so",
        "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**"
    )
}

construo {
    name.set(appName)
    humanName.set(appName)
    jlink {
        guessModulesFromJar.set(false)
        modules.addAll("java.base", "java.management", "java.desktop", "jdk.unsupported")
    }

    targets.register("linuxX64", Target.Linux::class.java).configure {
        architecture.set(Target.Architecture.X86_64)
        jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.10_7.tar.gz")
    }
    targets.register("macM1", Target.MacOs::class.java).configure {
        architecture.set(Target.Architecture.AARCH64)
        jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.10_7.tar.gz")
        identifier.set("com.prpg.$appName")
        macIcon.set(project.file("icons/logo.icns"))
    }
    targets.register("macX64", Target.MacOs::class.java).configure {
        architecture.set(Target.Architecture.X86_64)
        jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_x64_mac_hotspot_21.0.10_7.tar.gz")
        identifier.set("com.prpg.$appName")
        macIcon.set(project.file("icons/logo.icns"))
    }
    targets.register("winX64", Target.Windows::class.java).configure {
        architecture.set(Target.Architecture.X86_64)
        icon.set(project.file("icons/logo.png"))
        jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_x64_windows_hotspot_21.0.10_7.zip")
        // Uncomment the next line to show a console when the game runs, to print messages.
        // useConsole.set(true)
    }
}

// Equivalent to the jar task; here for compatibility with gdx-setup.
tasks.register("dist") {
    dependsOn("jar")
}

distributions {
    named("main") {
        contents {
            into("libs") {
                val jarOutput = tasks.named<Jar>("jar").get().outputs.files.singleFile.name
                project.configurations["runtimeClasspath"].files
                    .filter { it.name != jarOutput }
                    .forEach { exclude(it.name) }
            }
        }
    }
}

tasks.named("startScripts") {
    dependsOn(":lwjgl3:jar")
}
tasks.named<CreateStartScripts>("startScripts") {
    classpath = tasks.named<Jar>("jar").get().outputs.files
}

if (enableGraalNative == "true") {
    apply(from = "nativeimage.gradle.kts")
}
