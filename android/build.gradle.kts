import java.util.Properties

val appName: String by extra

plugins {
    // AGP is brought in via the root buildscript classpath (see classpath(libs.android.gradle.plugin)
    // in the root build.gradle.kts); we apply it here without a version since the version is
    // resolved transitively. The catalog still owns the AGP version coordinate.
    id("com.android.application")
}

android {
    namespace = "com.prpg"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src/main/java"))
            aidl.setSrcDirs(listOf("src/main/java"))
            renderscript.setSrcDirs(listOf("src/main/java"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf("../assets"))
            jniLibs.setSrcDirs(listOf("libs"))
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/robovm/ios/robovm.xml", "META-INF/DEPENDENCIES.txt", "META-INF/DEPENDENCIES",
                "META-INF/dependencies.txt", "**/*.gwt.xml"
            )
            pickFirsts += setOf(
                "META-INF/LICENSE.txt", "META-INF/LICENSE", "META-INF/license.txt", "META-INF/LGPL2.1",
                "META-INF/NOTICE.txt", "META-INF/NOTICE", "META-INF/notice.txt"
            )
        }
    }
    defaultConfig {
        applicationId = "com.prpg"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// Bundled content packs are staged into ../assets/packs/ (the Android assets srcDir) before AGP
// merges assets, so they ship in the APK and the runtime PackMounter can extract them.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(":stageBundledPacks") }

repositories {
    // needed for AAPT2, may be needed for other tools
    google()
}

val natives: Configuration by configurations.creating

dependencies {
    "coreLibraryDesugaring"(libs.android.desugar.jdk.libs)
    "implementation"(libs.gdx.backend.android)
    "implementation"(project(":core"))

    natives(variantOf(libs.gdx.freetype.platform) { classifier("natives-arm64-v8a") })
    natives(variantOf(libs.gdx.freetype.platform) { classifier("natives-armeabi-v7a") })
    natives(variantOf(libs.gdx.freetype.platform) { classifier("natives-x86") })
    natives(variantOf(libs.gdx.freetype.platform) { classifier("natives-x86_64") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-arm64-v8a") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-armeabi-v7a") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-x86") })
    natives(variantOf(libs.gdx.platform) { classifier("natives-x86_64") })
}

// Called every time gradle gets executed; takes the native dependencies of
// the natives configuration and extracts them into the proper libs/ folders
// so they get packed with the APK.
tasks.register("copyAndroidNatives") {
    doFirst {
        file("libs/armeabi-v7a/").mkdirs()
        file("libs/arm64-v8a/").mkdirs()
        file("libs/x86_64/").mkdirs()
        file("libs/x86/").mkdirs()

        natives.copy().files.forEach { jar ->
            val outputDir: File? = when {
                jar.name.endsWith("natives-armeabi-v7a.jar") -> file("libs/armeabi-v7a")
                jar.name.endsWith("natives-arm64-v8a.jar") -> file("libs/arm64-v8a")
                jar.name.endsWith("natives-x86_64.jar") -> file("libs/x86_64")
                jar.name.endsWith("natives-x86.jar") -> file("libs/x86")
                else -> null
            }
            if (outputDir != null) {
                copy {
                    from(zipTree(jar))
                    into(outputDir)
                    include("*.so")
                }
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn("copyAndroidNatives")
}

tasks.register<Exec>("run") {
    val localProperties = project.file("../local.properties")
    val path: String = if (localProperties.exists()) {
        val properties = Properties()
        localProperties.inputStream().use { properties.load(it) }
        properties.getProperty("sdk.dir") ?: System.getenv("ANDROID_SDK_ROOT")
    } else {
        System.getenv("ANDROID_SDK_ROOT")
    }
    val adb = "$path/platform-tools/adb"
    commandLine(
        adb, "shell", "am", "start", "-n",
        "com.prpg/com.prpg.android.AndroidLauncher"
    )
}

eclipse.project.name = "$appName-android"
