import java.util.Properties

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
    }
    dependencies {
        classpath(libs.android.gradle.plugin)
    }
}

allprojects {
    apply(plugin = "eclipse")
    apply(plugin = "idea")

    // This allows you to "Build and run using IntelliJ IDEA", an option in IDEA's Settings.
    configure<org.gradle.plugins.ide.idea.model.IdeaModel> {
        module {
            outputDir = file("build/classes/java/main")
            testOutputDir = file("build/classes/java/test")
        }
    }
}

configure(subprojects - project(":android")) {
    apply(plugin = "java-library")
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
    }

    // From https://lyze.dev/2021/04/29/libGDX-Internal-Assets-List/
    // Bundled content packs are staged into assets/packs/ first, so assets.txt lists them and the
    // runtime PackMounter can extract them out of the app. The walk runs in doLast (execution phase)
    // so it sees the freshly-staged packs.
    tasks.register("generateAssetList") {
        dependsOn(":stageBundledPacks")
        val assetsFolder = file("${project.rootDir}/assets/")
        val assetsFile = File(assetsFolder, "assets.txt")
        doLast {
            assetsFile.delete()
            fileTree(assetsFolder)
                .map { assetsFolder.toPath().relativize(it.toPath()).toString() }
                .sorted()
                .forEach { assetsFile.appendText(it + "\n") }
        }
    }
    tasks.named("processResources") { dependsOn("generateAssetList") }

    tasks.withType<JavaCompile>().configureEach {
        options.isIncremental = true
        options.encoding = "UTF-8"
    }
}

// `libs` accessor isn't available in cross-project blocks (subprojects/allprojects), so resolve
// the projectVersion via the runtime VersionCatalogsExtension API at root scope and reuse below.
val projectVersionFromCatalog: String =
    extensions.getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("projectVersion").orElseThrow { IllegalStateException("projectVersion missing in libs.versions.toml") }
        .requiredVersion

subprojects {
    version = projectVersionFromCatalog
    // The one place the app is named: drives jar/bundle names, the window title, the libGDX
    // Preferences name and the per-user content root (~/.<appName>/). See README "Renaming".
    extra["appName"] = "prpg"
    repositories {
        mavenCentral()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
    }
}

configure<org.gradle.plugins.ide.eclipse.model.EclipseModel> {
    project.name = "prpg-parent"
}

repositories {
    mavenCentral()
}

// =========================================================================================
// Content packs. A pack is one self-contained folder under packs/: an act (maps, tilesets, story,
// staging, activities, quests, flags) or the shared `baseline` (ui, fonts, config, items, player
// art, the Ink bridge, the act catalog). Packs with `bundled: true` in their pack.yaml ship inside
// the app; everything else is DLC, zipped by packPack and dropped into the content root.
// =========================================================================================

val packsSourceDir = file("packs")
val stagedPacksDir = file("assets/packs")

/** Every pack directory under packs/ (those with a pack.yaml), sorted by id. */
fun Project.packDirs(): List<File> =
    file("packs").listFiles { f -> f.isDirectory && File(f, "pack.yaml").exists() }
        ?.sortedBy { it.name } ?: emptyList()

/** Packs whose pack.yaml says `bundled: true`: shipped inside the app. */
fun Project.bundledPackDirs(): List<File> =
    packDirs().filter { com.prpg.build.PackTool.field(File(it, "pack.yaml"), "bundled") == "true" }

/** The -Ppack=<id> value, or fail with a usage message. */
fun Project.requirePackId(): String =
    (findProperty("pack") as? String)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("pass -Ppack=<id> (e.g. -Ppack=act2)")

fun packVersion(packDir: File): String =
    com.prpg.build.PackTool.field(File(packDir, "pack.yaml"), "version") ?: "1"

tasks.register<Copy>("stageBundledPacks") {
    description = "Stage bundled content packs (pack.yaml bundled: true) into assets/packs/ for shipping."
    group = "assets"
    // Rebuild cleanly so a file removed from a pack source doesn't linger in the staged copy.
    doFirst { delete(stagedPacksDir) }
    bundledPackDirs().forEach { dir ->
        from(dir) {
            into(dir.name)
            exclude("ink/**")        // Ink source is build-time only; compiled JSON ships under narrative/
            exclude("art/**")        // source art (atlases are packed separately)
            exclude("pack.meta.yaml")
        }
    }
    into(stagedPacksDir)
}

// --- Narrative pipeline: Ink -> JSON ---------------------------------------------------
//
// `./gradlew compileInk` compiles each pack's ink/<id>.ink to narrative/<id>.ink.json using the
// pure-JVM blade-ink compiler in buildSrc. INCLUDEs resolve against the pack's own ink/ first, then
// the baseline pack's ink/ (so every act can INCLUDE common/bridge.ink either way). Opt-in and
// committed, like the art pipeline, so CI never needs the Ink toolchain.

val commonInkRoot = file("packs/baseline/ink")

tasks.register("compileInk") {
    description = "Compile each pack's packs/<id>/ink/<id>.ink to packs/<id>/narrative/<id>.ink.json."
    group = "assets"
    inputs.dir(packsSourceDir).withPropertyName("packsSource")

    doLast {
        packDirs().forEach { packDir ->
            val actId = packDir.name
            val mainInk = File(packDir, "ink/$actId.ink")
            if (!mainInk.exists()) {
                return@forEach // baseline (and any pack with no story) has no <id>.ink
            }
            val json = com.prpg.build.InkCompiler.compile(mainInk, commonInkRoot)
            val outDir = File(packDir, "narrative").apply { mkdirs() }
            File(outDir, "$actId.ink.json").writeText(json)
            logger.lifecycle("compiled ink: $actId -> ${File(outDir, "$actId.ink.json")}")
        }
    }
}

// --- Pack packaging: manifests / zip / scaffold / bundle ---------------------------------

tasks.register("generatePackManifests") {
    description = "Regenerate the files: list in every pack's pack.yaml (the per-pack analogue of assets.txt)."
    group = "assets"
    doLast {
        packDirs().forEach {
            val files = com.prpg.build.PackTool.regenerateFiles(it)
            logger.lifecycle("regenerated ${it.name}/pack.yaml (${files.size} files)")
        }
    }
}

tasks.register("validatePacks") {
    description = "Run the content + pack-manifest guard rails over every pack (pre-ship gate)."
    group = "verification"
    dependsOn(":core:validateContent")
}

tasks.register("packPack") {
    description = "Zip one content pack for distribution: -Ppack=<id> -> build/packs/<id>-v<ver>.zip."
    group = "assets"
    dependsOn("generatePackManifests")
    dependsOn("compileInk")
    dependsOn(":core:validateContent")
    doLast {
        val id = requirePackId()
        val packDir = file("packs/$id")
        if (!packDir.isDirectory) throw GradleException("no pack at packs/$id")
        val outZip = layout.buildDirectory.file("packs/$id-v${packVersion(packDir)}.zip").get().asFile
        com.prpg.build.PackTool.zipRuntime(packDir, outZip)
        logger.lifecycle("packed $id -> $outZip")
    }
}

// One-command ship pipeline for a single pack: generatePackManifests -> compileInk -> validateContent
// -> zip. Saves authors the daemon-less cost of four separate invocations.
tasks.register("shipPack") {
    description = "Full pipeline for one pack: regenerate manifests, compile Ink, validate, zip. -Ppack=<id>."
    group = "assets"
    dependsOn("packPack")
    doLast {
        val id = requirePackId()
        val zip = layout.buildDirectory.file("packs/$id-v${packVersion(file("packs/$id"))}.zip").get().asFile
        logger.lifecycle("shipped $id -> $zip")
        logger.lifecycle("  install: drop it (or its unzipped folder) into the content root (~/.prpg/content/).")
    }
}

tasks.register("packAll") {
    description = "Zip every content pack under packs/ for distribution."
    group = "assets"
    dependsOn("generatePackManifests")
    dependsOn("compileInk")
    dependsOn(":core:validateContent")
    doLast {
        packDirs().forEach {
            val outZip = layout.buildDirectory.file("packs/${it.name}-v${packVersion(it)}.zip").get().asFile
            com.prpg.build.PackTool.zipRuntime(it, outZip)
            logger.lifecycle("packed ${it.name} -> $outZip")
        }
    }
}

tasks.register("newPack") {
    description = "Scaffold a new act pack with a bootable stub map: -Ppack=<id> [-Pkind=act|epilogue] [-Porder=N]."
    group = "assets"
    doLast {
        val id = requirePackId()
        val kind = (findProperty("kind") as? String) ?: "act"
        val order = (findProperty("order") as? String)?.toIntOrNull() ?: (packDirs().size)
        val packDir = file("packs/$id")
        if (packDir.exists()) throw GradleException("packs/$id already exists")
        com.prpg.build.PackScaffold.scaffold(packDir, id, kind, order)
        logger.lifecycle("scaffolded packs/$id (kind=$kind, order=$order): edit pack.yaml, ink/$id.ink, maps/${id}_start.tmx, staging/$id.yaml; then ./gradlew shipPack -Ppack=$id")
    }
}

tasks.register("bundleDlc") {
    description = "Group built pack zips into one DLC bundle: -Pdlc=<id> -Ppacks=act2,act3."
    group = "assets"
    dependsOn("packAll")
    doLast {
        val dlc = (findProperty("dlc") as? String)?.takeIf { it.isNotBlank() }
            ?: throw GradleException("pass -Pdlc=<id>")
        val packs = ((findProperty("packs") as? String) ?: throw GradleException("pass -Ppacks=a,b,c"))
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val outDir = layout.buildDirectory.dir("dlc/$dlc").get().asFile
        delete(outDir)
        outDir.mkdirs()
        val sb = StringBuilder("# DLC bundle: $dlc (one purchase delivering ${packs.size} pack(s)).\nid: $dlc\npacks:\n")
        packs.forEach { id ->
            val zip = layout.buildDirectory.file("packs/$id-v${packVersion(file("packs/$id"))}.zip").get().asFile
            if (!zip.exists()) throw GradleException("missing zip for $id (run packAll first)")
            copy { from(zip); into(outDir) }
            sb.append("  - $id\n")
        }
        File(outDir, "dlc.yaml").writeText(sb.toString())
        logger.lifecycle("bundled DLC '$dlc' (${packs.size} packs) -> $outDir")
    }
}

// =========================================================================================
// Art pipeline. Three opt-in steps, all committed:
//   genPlaceholderArt          -> packs/baseline/tilesets/basic.png + packs/baseline/art/*.png sheets
//   genCharacterAseprites      -> packs/baseline/art/*.aseprite (needs Aseprite; art/tools/*.lua)
//   buildAtlases -Ppack=<id>   -> packs/<id>/atlases/<id>.atlas (Aseprite export + TexturePacker)
// CI never runs any of these; only contributors who touch art do.
// =========================================================================================

val texturePacker: Configuration = configurations.create("texturePacker")
dependencies {
    texturePacker(libs.gdx.tools)
    texturePacker(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}

/** Resolves {@code aseprite.bin} from local.properties (with ~ expansion), or null if unset. */
fun Project.asepriteBin(): String? {
    val localProps = file("local.properties")
    if (!localProps.exists()) return null
    val raw = Properties().apply { localProps.inputStream().use { load(it) } }
        .getProperty("aseprite.bin") ?: return null
    return if (raw.startsWith("~")) System.getProperty("user.home") + raw.drop(1) else raw
}

fun Project.requireAseprite(): String {
    val bin = asepriteBin() ?: throw GradleException("aseprite.bin is not set in local.properties (see README, Art pipeline).")
    if (!file(bin).canExecute()) throw GradleException("aseprite.bin ($bin) is not an executable file.")
    return bin
}

tasks.register("genPlaceholderArt") {
    description = "Regenerate the placeholder tileset and character sheets (pure Java, no external tools)."
    group = "assets"
    doLast {
        com.prpg.build.PlaceholderArt.generate(file("packs/baseline"))
        logger.lifecycle("wrote packs/baseline/tilesets/basic.png and packs/baseline/art/{player,npc}.png")
    }
}

tasks.register("genCharacterAseprites") {
    description = "Convert packs/baseline/art/*.png character sheets into tagged .aseprite files (needs Aseprite)."
    group = "assets"
    doLast {
        val bin = requireAseprite()
        val process = ProcessBuilder(bin, "-b", "-script", file("art/tools/gen_character_aseprites.lua").absolutePath)
            .directory(rootDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) throw GradleException("aseprite script failed:\n$output")
        logger.lifecycle(output.trim())
    }
}

/** Aseprite-export + TexturePacker for one pack's art -> packs/<id>/atlases/<id>.atlas. */
fun Project.buildAtlasForPack(packId: String) {
    val artDir = file("packs/$packId/art")
    if (!artDir.isDirectory) {
        logger.lifecycle("pack $packId: no art/ directory, skipping atlas.")
        return
    }
    val sources = artDir.listFiles { f -> f.isFile && f.extension == "aseprite" }?.sortedBy { it.name }
        ?: emptyList()
    if (sources.isEmpty()) {
        logger.lifecycle("pack $packId: art/ has no .aseprite files, skipping atlas.")
        return
    }
    val bin = requireAseprite()

    val frameDir = layout.buildDirectory.dir("aseprite-frames/$packId").get().asFile
    delete(frameDir)
    frameDir.mkdirs()
    sources.forEach { source ->
        // Each tag becomes one region name (<stem>_<tag>), with per-frame indices.
        val pattern = "${frameDir.absolutePath}/${source.nameWithoutExtension}_{tag}_{tagframe}.png"
        val cmd = listOf(bin, "-b", "--split-tags", source.absolutePath, "--save-as", pattern)
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) throw GradleException("aseprite failed on ${source.name}:\n$output")
    }
    // TexturePacker reads pack.json for pixel-art-safe settings (Nearest filter, 2px dup padding).
    File(frameDir, "pack.json").writeText(
        """
        {
          "filterMin": "Nearest",
          "filterMag": "Nearest",
          "duplicatePadding": true,
          "paddingX": 2,
          "paddingY": 2,
          "bleed": true,
          "edgePadding": true
        }
        """.trimIndent()
    )
    val atlasDir = file("packs/$packId/atlases").apply { mkdirs() }
    val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
    val tp = ProcessBuilder(
        javaBin, "-cp", configurations["texturePacker"].asPath,
        "com.badlogic.gdx.tools.texturepacker.TexturePacker",
        frameDir.absolutePath, atlasDir.absolutePath, packId
    ).redirectErrorStream(true).start()
    val tpOut = tp.inputStream.bufferedReader().readText()
    if (tp.waitFor() != 0) throw GradleException("TexturePacker failed for pack $packId:\n$tpOut")
    logger.lifecycle("built atlas: packs/$packId/atlases/$packId.atlas")
}

tasks.register("buildAtlases") {
    description = "Build the texture atlas for one pack (Aseprite -> TexturePacker): -Ppack=<id>."
    group = "assets"
    doLast { buildAtlasForPack(requirePackId()) }
}

tasks.register("buildAtlasesAll") {
    description = "Build atlases for every pack that has an art/ directory."
    group = "assets"
    doLast {
        packDirs().forEach { buildAtlasForPack(it.name) }
    }
}
