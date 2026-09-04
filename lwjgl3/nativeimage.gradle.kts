// Applied from lwjgl3/build.gradle.kts when enableGraalNative=true.

apply(plugin = "org.graalvm.buildtools.native")

val appName: String by extra

configure<org.graalvm.buildtools.gradle.dsl.GraalVMExtension> {
    binaries {
        named("main") {
            imageName.set(appName)
            mainClass.set(application.mainClass)
            requiredVersion.set("23.0")
            buildArgs.add("-march=compatibility")
            jvmArgs.addAll("-Dfile.encoding=UTF8")
            sharedLibrary.set(false)
            resources.autodetect()
        }
    }
}

tasks.named<JavaExec>("run") {
    doNotTrackState("Running the app should not be affected by Graal.")
}

// Modified from https://lyze.dev/2021/04/29/libGDX-Internal-Assets-List/
// Creates a resource-config.json file based on the contents of the assets folder.
// This file is used by Graal Native to embed those specific files.
tasks.named("generateResourcesConfigFile") {
    doFirst {
        val assetsFolder = File("${project.rootDir}/assets/")
        val resFolder = File("${project.projectDir}/src/main/resources/META-INF/native-image/$appName")
        resFolder.mkdirs()
        val resFile = File(resFolder, "resource-config.json")
        resFile.delete()
        resFile.appendText(
            """{
  "resources":{
  "includes":[
    {
      "pattern": ".*("""
        )
        fileTree(assetsFolder).forEach {
            resFile.appendText("\\Q${it.name}\\E|")
        }
        resFile.appendText(
            """libgdx.+\.png|lsans.+)"
    }
  ]},
  "bundles":[]
}"""
        )
    }
}
