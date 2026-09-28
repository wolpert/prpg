# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## How Claude works here

Claude is a **helper on this project, not its driver**. The humans on the project decide what the
game is, what gets built next, and how the architecture evolves. Concretely:

- Do the task that was asked, at the scope that was asked. Don't widen it into a redesign, a
  rename sweep, a "while I'm here" refactor, or a new feature nobody requested.
- Don't set direction. Don't propose or start a roadmap, pick a genre, invent story, choose a
  monetisation model, or restructure the packs unless explicitly asked to. If a task can't be done
  well without such a decision, say so and stop at that decision point.
- Preserve the existing seams (below). If a change would need a new seam or would bypass one,
  say that in a sentence and ask before doing it.
- Content is Ink first. An act's story (`packs/<act>/ink/<act>.ink`) is the source of truth for what
  happens; prefer editing Ink, then TMX/YAML, over writing Java. Write Java only where the guide
  says Java is required (a new activity type, story command, scripted event or bridge function).
- Verify before claiming. Run `./gradlew :core:test` (or `validatePacks` for content-only changes)
  and report the actual result, including failures.
- No em dashes in prose or comments.

## What this is

**prpg** is a template for a 2D top-down "pseudo-RPG": tile-map exploration, dialogue, small
activities (minigames), structured into **acts** that are also the **content packs** and the **DLC**
unit. The game designer builds each act as one Ink story; the engine plays it. Built on libGDX;
Java package root is `com.prpg`. `README.md` is the human-facing guide (getting started, renaming,
commands); `docs/content-guide.md` is the content-authoring contract. Both are canonical; this file
is the short map for Claude.

## Project shape

Java 21, Gradle (Kotlin DSL), multi-module (`settings.gradle.kts` includes `core`, `lwjgl3`,
`android`):

- `core/` game code (`com.prpg.TheGame` extends libGDX `Game`) + the Dagger graph.
- `lwjgl3/` desktop launcher; `android/` Android launcher (`applicationId`/`namespace = com.prpg`).
- `packs/` the canonical content tree. `baseline/` holds shared engine content (ui skin, fonts,
  `config/game.yaml`, `i18n`, `items/items.yaml`, `ink/common/bridge.ink` (engine functions),
  `ink/common/world.ink` (story variables shared between acts), `narrative/manifest.yaml`, the
  player sprite + atlas, the placeholder tileset). Every other pack is an **act**: its `ink/`
  story (state, `cast()`, `stage()`, `journal()`, every knot), its own `maps/` (geography only) and
  `activities/` (tuning only). **There is no base map**: line 1 of each act's Ink is
  `# entry: <map> <spawn>`.
- `assets/` bundled-pack staging area (`assets/packs/` and `assets.txt` are generated, gitignored).
- `art/` the Tiled project (`art/prpg.tiled-project`) and the Aseprite Lua tool. Per-pack source art
  lives in `packs/<id>/art/`.
- `buildSrc/` build tools: `InkCompiler`, `PackTool`, `PackScaffold`, `PlaceholderArt`.

`org.gradle.daemon=false` is deliberate; every `./gradlew` is a fresh JVM.

## Commands

```bash
./gradlew lwjgl3:run                 # run the desktop build
./gradlew :core:test                 # unit tests (JUnit 5 + Mockito)
./gradlew validatePacks              # content guard rails only (the pre-ship gate)
./gradlew build test --stacktrace    # what CI runs

./gradlew compileInk                 # packs/<id>/ink/<id>.ink -> narrative/<id>.ink.json (commit it)
./gradlew generatePackManifests      # refresh each pack.yaml files: list (commit it)
./gradlew newPack -Ppack=act3        # scaffold a bootable act
./gradlew shipPack -Ppack=act2       # manifests + ink + validate + zip -> build/packs/act2-v1.zip
./gradlew genPlaceholderArt          # regenerate placeholder tileset/sheets (pure Java)
./gradlew genCharacterAseprites      # sheets -> .aseprite (needs Aseprite)
./gradlew buildAtlases -Ppack=<id>   # .aseprite -> packs/<id>/atlases/<id>.atlas (needs Aseprite)
```

After editing content, clear the dev content root (`rm -rf ~/.prpg/content`) or the game keeps the
copy it extracted on first run (packs re-extract only when `pack.yaml` `version:` rises).

## Architecture (the seams)

- **Dagger.** `di/GameComponent` = `CoreModule` (engine, menu screens, scaffold ECS systems) +
  `world/WorldModule` (world screen, story commands, activities, cutscenes) +
  `narrative/NarrativeModule`. Every content-facing registry is a Dagger multibinding keyed by the
  string Ink uses: `@StringKey("<name>") StoryCommand` for `>>>` commands (also listed in
  `world/command/Commands.ALL` for the validators), `@StringKey("<type>") FullScreenActivity` /
  `PopupActivity` for activities, `@StringKey("<id>") ScriptedEvent` for cutscenes,
  `@ScreenKey(X.class) Screen` for screens, `@IntoSet EntitySystem`.
- **Content resolution.** `content/ContentResolver` turns a logical path (`maps/x.tmx`) into a
  `FileHandle`: mounted packs first (`PackRegistry`), then bundled assets. `PackMounter` extracts
  bundled packs and installs dropped zips into `~/.prpg/content/` before the graph resolves
  content-backed singletons. Never call `Gdx.files.internal` for content.
- **Acts are data.** `narrative/content/ActContentRegistry` builds the act spine from
  `narrative/manifest.yaml` merged with each mounted pack's `provides:` (id, title, order), and
  reads each act's entry from its story's `# entry:` global tag. `ActProgression` walks it with
  `GateResult` gates (narrative / owned / installed). `WorldScreen` moves the player to the new
  act's entry and runs its `act_start` knot when the act changes. No act enum.
- **Narrative (Ink is the source of truth).** blade-ink. Story state is Ink variables: Ink declares
  and changes them, `narrative/StoryVariables` keeps them by name (pushed into a story before it
  runs, captured after every step, saved), and acts share one by declaring it once in `world.ink`.
  `NarrativeRunner` drives one cached `Story` per act: knots (a staged thing's interaction), `>>>`
  command lines (handed to a `StoryCommand` while the story waits), and `evaluate()` of the act's
  `stage()` / `cast()` / `journal()` functions. `StateBridge` binds the `EXTERNAL`s in `bridge.ink`:
  inventory, act spine, and the world-description calls (reads lookahead-safe, writes not).
  `InkContentValidationTest` enforces the bridge contract; `InkRuntimeContractTest` pins the
  blade-ink behaviours this relies on.
- **World.** `WorldScreen` renders the TMX (`MapManager`, grid collision, no physics), ticks the
  Ashley engine, hosts overlays and popups, and carries out `WorldTravel` requests (portals,
  `>>> go`). `WorldEntityFactory` spawns the map's portals and the story's staging
  (`world/stage/StageDirector`: the act's `stage()` re-evaluated whenever variables, the act or the
  conversation move; placements name map **markers**, never coordinates). Maps hold only geography.
- **Activities.** `activities/ActivityLauncher` dispatches a `type` string to a
  `FullScreenActivity` (`BaseActivityScreen`: match3, merge) or a `PopupActivity`
  (`BasePopupActivity`: lightsout). Tuning lives in `activities/<type>/<id>.yaml`. The story plays
  one with `>>> play <type> <id>` (`world/command/PlayCommand`) and branches on `activity_won`.
- **Save.** `save/SaveManager` writes one `save.json` (schema v2) under `ContentRoot`: story
  variables, inventory, act spine, Ink resume tokens. Staging and the journal are re-derived.
- **Guard rails** (pure JVM, run by `validatePacks`): `ContentValidationTest`,
  `InkWorldValidationTest`, `InkContentValidationTest`, `PackConsistencyTest`,
  `TilesetValidationTest`, `CharacterSpriteContentTest`. Run them after any content edit.

## Conventions

- YAML POJOs use public fields and live in a leaf `config` package (the Android R8 keep rule
  matches `**.config.**`).
- Logging goes through `util/Log` (null-safe for headless tests); tag = class simple name; the
  message names the offending id/path and the fallback taken. See `docs/logging.md`.
- Story variables are declared in exactly one `.ink` file (shared ones in `world.ink`); names
  starting `meta_` survive a new game. There are no flags or staging/quest/actor YAML files.
- Tests are headless: pure-JVM logic, Mockito for collaborators, `gdx-backend-headless` on the
  classpath. Nothing in `core/src/test` may need a GL context.
