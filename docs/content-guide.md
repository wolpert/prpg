# Content guide

The contract between content files and the engine: how to add maps, characters, dialogue, items,
activities, quests, cutscenes and acts, and how each links to in-game behaviour.

Everything here is **data-driven**. The engine reads content by convention (layer names, property
names, file ids), so most new content needs no Java. The only things that need Java are a new
**activity type**, a new **cutscene**, and a new **bridge function** (each is one small class or
method plus one Dagger binding, shown below).

**Core principle:** *Java owns durable state; Ink stories are ephemeral.* The flag store, the
inventory and the save file are canonical; dialogue reads and writes them through the bridge but
holds no truth of its own.

## Contents

1. [The pack model](#1-the-pack-model)
2. [Where content lives](#2-where-content-lives)
3. [Recipes](#3-recipes)
4. [Maps: the TMX contract](#4-maps-the-tmx-contract)
5. [Staging: placing act content](#5-staging-placing-act-content)
6. [Dialogue and story (Ink)](#6-dialogue-and-story-ink)
7. [Activities](#7-activities)
8. [Flags, items, quests, cutscenes](#8-flags-items-quests-cutscenes)
9. [Art](#9-art)
10. [Validate, build, ship](#10-validate-build-ship)
11. [YAML reference](#11-yaml-reference)

---

## 1. The pack model

| Term | Meaning |
| --- | --- |
| **Pack** | The unit you *build*: one folder under `packs/`. Either an **act** or the shared **baseline**. |
| **Act** | A pack that provides a chapter of the game: its own maps, story, staging, activities, quests, flags. Acts are ordered; the player moves through them one way. |
| **DLC** | The unit you *sell*: one or more pack zips grouped in `manifest.yaml`'s `dlc:` section. |
| **baseline** | The shared pack every act references: ui, fonts, config, the item catalog, the Ink bridge, the act catalog, the player sprite, the placeholder tileset. It has **no maps**. |

- A pack with `bundled: true` in its `pack.yaml` ships inside the app (the build stages it into
  `assets/packs/`). Every other pack is DLC: `./gradlew shipPack -Ppack=<id>` zips it, and dropping
  the zip (or the unzipped folder) into the content root installs it on next launch.
- Each pack's `pack.yaml` declares the act it **provides** (id, title, order, entry map + spawn),
  its declared flags, and a generated `files:` list. A mounted pack is discovered automatically;
  the catalog (`packs/baseline/narrative/manifest.yaml`) only needs an entry to *advertise* an act
  that isn't installed.
- **Content root** (where mounted packs live at runtime): desktop `~/.prpg/content/`, Android the
  app-private `content/` dir. Bundled packs are extracted there on first run and re-extracted only
  when `pack.yaml` `version:` rises.

> **Entitlement is not enforced.** `DefaultEntitlement` owns anything catalogued `FREE` or
> mounted, plus any act with a durable `meta.owns.<act>` flag. That flag is the seam a real store
> writes through (`DefaultEntitlement.grant(actId)`); drop the "mounted == owned" rule when you
> wire one up.

## 2. Where content lives

Paths are relative to a pack folder. At runtime everything resolves through `ContentResolver`,
which looks in mounted packs first (acts before baseline), then bundled assets, so an act can ship
its own copy of a shared file and shadow the baseline.

| Kind | Location | Pack | Loaded by |
| --- | --- | --- | --- |
| Maps | `maps/<id>.tmx` | the act | `world/MapManager` |
| Tilesets | `tilesets/<name>.tsx` + `.png` | baseline (shared) or the act | referenced from the TMX by relative path |
| Staging | `staging/<actId>.yaml` | the act | `world/stage/StageDirector` |
| Actors | `actors/<id>.yaml` | the act (or baseline for a recurring cast) | `world/stage/ActorRegistry` |
| Story | `ink/<actId>.ink` -> `narrative/<actId>.ink.json` | the act | `narrative/NarrativeRunner` |
| Bridge | `ink/common/bridge.ink` | baseline | INCLUDEd by every act's story |
| Act catalog | `narrative/manifest.yaml` | baseline | `narrative/content/ActContentRegistry` |
| Activities | `activities/<type>/<id>.yaml` | the act | `activities/*` |
| Quests | `quests/<id>.yaml` | the act | `quests/QuestLog` |
| Items | `items/items.yaml` | baseline | `items/ItemRegistry` |
| Flags | `flags.yaml` | the act (`meta.*` in baseline) | the validation tests |
| Config | `config/game.yaml` | baseline | `config/GameConfig` |
| Strings | `i18n/strings.properties` | baseline | `ui/Strings` |
| Sprites | `sprites/<id>.sprite.yaml` + `atlases/<pack>.atlas` | the pack that owns the art | `world/CharacterSpriteLoader` |
| Source art | `art/*.aseprite`, `art/*.png` | the pack that owns the art | the build only |

## 3. Recipes

Each recipe is the complete list of edits for one thing. The sample act (`packs/act1`) contains a
worked example of every one.

### 3.1 A new act

```bash
./gradlew newPack -Ppack=act3 -Porder=3
```

This writes `packs/act3/` with a `pack.yaml` (`bundled: false`, `provides:` act3 with
`entryMap: act3_start`), a bootable stub map `maps/act3_start.tmx` (walled field, spawn `start`,
marker `centre`), `ink/act3.ink` with one knot, an empty `staging/act3.yaml` and `flags.yaml`. Then:

1. Edit `pack.yaml`: real `title:`, and `bundled: true` if the act ships in the app.
2. Author maps (section 4), staging (5), story (6), activities (7), quests and flags (8).
3. If the act should be advertised before it is installed, add it to
   `packs/baseline/narrative/manifest.yaml` (`source: DLC`, `entitlement: PAID`) and a `dlc:` group.
4. Make the previous act hand off to it: in that act's Ink, `~ unlock_act("act3")` at the story
   beat, then `~ advance_act()` (see 6.3 for the gate check).
5. `./gradlew shipPack -Ppack=act3` (regenerates the manifest, compiles Ink, validates, zips).
   Commit the regenerated `narrative/act3.ink.json` and `pack.yaml` `files:` list.

### 3.2 A character who talks

```yaml
# 1. Who: packs/act1/actors/keeper.yaml
id: keeper
name: Keeper
sprite: npc            # sprites/npc.sprite.yaml (optional; falls back to color)
color: C06A2E
```
```xml
<!-- 2. Where they can stand: a named object in the map's `markers` layer -->
<object id="10" name="keeper_post" type="Marker" x="112" y="144" width="16" height="16"/>
```
```yaml
# 3. Where they ARE this act: packs/act1/staging/act1.yaml
cast:
  - actor: keeper
    at: { map: gatehouse_yard, marker: keeper_post }
    dialogue: keeper_greeting
```
```ink
// 4. What they say: packs/act1/ink/act1.ink. The knot name IS the dialogue id.
=== keeper_greeting ===
~ set_flag("act1.met_keeper")
You're the new hand? # speaker: Keeper
-> END
```
```yaml
# 5. Declare the flag: packs/act1/flags.yaml
flags:
  - act1.met_keeper
```

To move them later, add a second `cast:` entry further down with `when: [<flag>]` (the last
matching entry wins). To remove them, `unless: [<flag>]`.

### 3.3 An activity that blocks a doorway

```xml
<!-- 1. A marker sized to the area to bar -->
<object id="11" name="hall_door" type="Marker" x="160" y="16" width="16" height="16"/>
```
```yaml
# 2. The definition: packs/act1/activities/match3/hedge.yaml (filename base = activity id)
id: hedge
board: { width: 6, height: 6, pieces: [bramble, leaf, thorn] }
win: { per_type: { bramble: 6, leaf: 6, thorn: 6 } }
on_complete:
  set_flag: act1.hedge_cleared
  dialogue: hedge_cleared
```
```yaml
# 3. Put it in the world: staging obstacles
obstacles:
  - id: hedge
    at: { map: gatehouse_yard, marker: hall_door }
    solid: true
    activity: { type: match3, id: hedge }
    cleared_when: act1.hedge_cleared      # SAME flag as on_complete.set_flag
```

Solve it, the flag sets, the world refreshes, the obstacle stops rendering *and* stops blocking.
The `on_complete.set_flag` / `cleared_when` pairing is the whole mechanism. Drop `solid: true` for
something that is merely *in* the room; drop `activity:` for scenery that only has a line.

### 3.4 A popup activity

Same as 3.3 with `type: lightsout` (or any type bound as a `PopupActivity`). The world stays on
screen, frozen, and the panel closes itself after the solve. Nothing else differs for content.

### 3.5 An item to pick up

Items are declared once in `packs/baseline/items/items.yaml`; place one with a staging `items:`
entry, grant one from Ink (`~ give_item("brass_token")`) or from an activity's `on_complete.give_item`.

### 3.6 A trigger or cutscene

```yaml
triggers:
  - id: gate_arch
    at: { map: gatehouse_yard, marker: gate_arch }
    require_flag: act1.met_keeper
    set_flag: act1.crossed_gate
    fire_once: true
    event: fade_beat                      # a registered ScriptedEvent id (section 8.4)
```

### 3.7 A quest

```yaml
# packs/act1/quests/first_errand.yaml
id: first_errand
title: "@quest.first_errand.title"       # an i18n key or a literal
steps:
  - { id: meet,  text: "Find the keeper in the yard.", flag: act1.met_keeper }
  - { id: hedge, text: "Clear the hedge.",             flag: act1.hedge_cleared }
```

Every `flag` must be declared and set by something (dialogue, trigger, activity).

### 3.8 The loop after each change

```bash
rm -rf ~/.prpg/content       # or the game keeps the copy it extracted last time
./gradlew compileInk         # only after editing .ink; commit the JSON
./gradlew validatePacks      # the gate: every cross-reference, every flag, every manifest
./gradlew lwjgl3:run
```

## 4. Maps: the TMX contract

Author maps in Tiled via `art/prpg.tiled-project` (it points its folder list at `packs/` and
declares the object classes below). Orthogonal, **16x16** tiles, saved as `packs/<act>/maps/<id>.tmx`.
The filename without `.tmx` is the map id used everywhere else.

### Tile layers

| Layer | Required | Purpose |
| --- | --- | --- |
| `ground` | yes | Base terrain. Sets the tile size and the map's pixel bounds. Drawn below entities. |
| `walls` | no | Decoration drawn with `ground`, below entities. Visual only. |
| `collision` | recommended | **Any non-empty cell blocks movement.** Not drawn. Out-of-bounds always blocks. |
| `overlay` | no | Drawn *above* entities (canopy, roof edges). |

### Object layers

| Layer | Class | What it does |
| --- | --- | --- |
| `spawn` | `Spawn` | Where the player's **feet** land. The object's name is the handle portals and `entrySpawn` target. |
| `portals` | `Portal` | Walk-in zone: `target_map`, `target_spawn`, optional `require_flag`. |
| `markers` | `Marker` | Named anchor points staging places content at. The rectangle is the placed thing's footprint. |
| `npcs` | `NPC` | Map-owned props with a `dialogueId` (+ optional `color`). An act can re-voice one via staging `props:`. |
| `items` | `Item` | A pick-up: `itemId`. |
| `activities` | `Activity` | A walk-up launcher: `type`, `activityId`, optional `clearedFlag`, `color`. Cannot block; prefer a staged obstacle. |
| `triggers` | `Trigger` | Walk-in zone: `set_flag`, `require_flag`, `fire_once`, `event`. |

Keep all seven groups in every map (empty is fine). **A map holds what doesn't change** (terrain,
collision, spawns, portals, markers, furniture). Anything act-scoped is staged (section 5), so a map
can be populated differently in different acts and re-laid-out without breaking act content.

`color` properties are plain strings (`RRGGBB`, no `#`), not Tiled's colour type.

### Tilesets

Maps reference **external** `.tsx` files by relative path. Because packs extract to sibling folders
under one content root, the path climbs out of the pack:

| Map lives in | Tileset lives in | `source` |
| --- | --- | --- |
| an act pack | baseline (shared) | `../../baseline/tilesets/<name>.tsx` |
| an act pack | the same act pack | `../tilesets/<name>.tsx` |

A wrong climb depth does not error; the map renders blank tiles. The placeholder set is
`baseline/tilesets/basic.tsx` (8x4 tiles; the table is in `buildSrc/.../PlaceholderArt.java`).
`TilesetValidationTest` checks every `.tsx` against its PNG.

### Act entry

`pack.yaml` `provides: entryMap` / `entrySpawn` name the map and spawn the act opens on. A new game
starts at the lowest-ordered act's entry; advancing an act moves the player to the next act's entry.
`ContentValidationTest` checks the entry map is a TMX in the same pack and the spawn exists.

## 5. Staging: placing act content

`staging/<actId>.yaml` is the act's whole population, resolved by `StageDirector` against the flag
store every time the flags (or an Ink override) change.

```yaml
act: act1
cast:                           # characters; ordered story-early -> story-late; LAST match wins
  - actor: keeper
    at: { map: gatehouse_yard, marker: keeper_post }
    dialogue: keeper_greeting
  - actor: keeper
    at: { map: gatehouse_hall, marker: keeper_desk }
    dialogue: keeper_inside
    when: [act1.hedge_cleared]
obstacles:                      # things that occupy space (optionally block, optionally launch an activity)
  - id: hedge
    at: { map: gatehouse_yard, marker: hall_door }
    solid: true
    activity: { type: match3, id: hedge }
    cleared_when: act1.hedge_cleared
    color: 2E5A24
items:
  - { itemId: travel_ration, at: { map: gatehouse_hall, marker: table } }
triggers:
  - { id: gate_arch, at: { map: gatehouse_yard, marker: gate_arch }, set_flag: act1.crossed_gate, fire_once: true, event: fade_beat }
props:                          # per-act dialogue for map-owned furniture (npcs layer objects)
  - { prop: notice_board, dialogue: notice_board }
```

Rules:

- `when` = present only while *every* listed flag is set; `unless` = absent while *any* is.
- For each actor the **last** cast entry whose conditions hold wins; no match means the actor is
  not in the world. Obstacles, items and triggers are independent.
- `solid` is re-derived on every refresh: `solid AND all(solid_when) AND none(solid_unless)`. A
  blocker that appears on top of the player is inert until they step clear.
- An obstacle with `cleared_when` is gone for good once that flag is set.
- Ink can override the rules mid-act: `place_actor`, `remove_actor`, `set_actor_dialogue`,
  `set_solid`, and read `staged_on`. Overrides are the only staging state saved; entering the next
  act drops them (its own file is authoritative).
- `props:` exists because a prop's `dialogueId` names a knot in *one* act's story; each act
  re-voices (or silences) the furniture it cares about.

`StagingValidationTest` fails on any unknown actor, map, marker, knot (checked against **that
act's** Ink), flag, item or activity, and on `place_actor("...")` literals naming missing markers.

## 6. Dialogue and story (Ink)

### 6.1 Knots are dialogue ids

Interacting with a character diverts into the knot named by its `dialogue` inside the **current
act's** story. `# speaker: Name` tags the speaker; `# event: <id>` hands off to a cutscene when that
line shows. A knot ends with `-> END`. Every act's `.ink` starts with
`INCLUDE ../../baseline/ink/common/bridge.ink` (the explicit path lets Inky resolve it too).

### 6.2 The bridge

Declared in `bridge.ink`, bound by `StateBridge`; `InkContentValidationTest` keeps the two in sync.

| Function | Kind | Effect |
| --- | --- | --- |
| `has_flag(name)` / `flag_value(name)` | read | Flag set? / its int value. |
| `has_item(id)` / `item_count(id)` | read | Inventory queries. |
| `act_unlocked(id)` / `owns_act(id)` / `current_act()` | read | Act spine queries. |
| `next_act_gate()` | read | Why the next act can or can't be entered: `"ADVANCED"`, `"BLOCKED_NARRATIVE"`, `"BLOCKED_NOT_OWNED"`, `"BLOCKED_NOT_INSTALLED"`, `"COMPLETE"`. |
| `day()` | read | The in-fiction day. |
| `staged_on(id, map)` | read | Is that actor/obstacle on that map right now? |
| `set_flag(name)` / `set_flag_value(name, n)` / `clear_flag(name)` | write | Flags. |
| `give_item(id)` / `take_item(id)` | write | Inventory. |
| `unlock_act(id)` | write | Narrative unlock. |
| `advance_act()` | write | Enter the next act if its gates allow. |
| `advance_day()` | write | Day counter. |
| `place_actor(id, map, marker)` / `remove_actor(id)` / `set_actor_dialogue(id, knot)` / `set_solid(id, bool)` | write | Staging overrides. |

Reads are lookahead-safe; writes are not, so a side effect fires exactly once. To add a function:
declare it (with an Inky fallback) in `bridge.ink`, add its name to the matching list in
`StateBridge`, bind it in `bindReads`/`bindWrites`.

### 6.3 Ending an act

```ink
~ unlock_act("act2")
{ next_act_gate():
    - "ADVANCED":
        ~ advance_act()
        Go on, then. The gate is open. # speaker: Keeper
    - "BLOCKED_NOT_OWNED":
        The road beyond isn't yours yet. # speaker: Keeper
    - "BLOCKED_NOT_INSTALLED":
        The road beyond isn't built yet. # speaker: Keeper
    - else:
        The road is closed for now. # speaker: Keeper
}
```

After the conversation ends the world screen notices the act changed and moves the player to the
new act's entry map (with an autosave).

### 6.4 Compile

`./gradlew compileInk` writes `narrative/<act>.ink.json`; commit it. Tests compile Ink on the fly,
so they never depend on the committed JSON, but the game and CI do.

## 7. Activities

An activity is a YAML definition in `activities/<type>/<id>.yaml` plus something in the world that
launches it (a staged obstacle's `activity:` or an `activities`-layer object). The `type` is the
key a Java class is bound under in `WorldModule`; the `id` is the filename base.

Two presentations, chosen by the Java class, invisible to content:

- **Full-screen** (`FullScreenActivity`, base class `BaseActivityScreen`): replaces the world screen;
  Esc/Back returns. Samples: `match3`, `merge`.
- **Popup** (`PopupActivity`, base class `BasePopupActivity`): a panel over the frozen world; Esc,
  Back or the Close button dismiss it; a solve closes it after a short pause. Sample: `lightsout`.

Every definition shares the same `on_complete` block, applied by `OnCompleteApplier`:

```yaml
on_complete:
  set_flag: act1.strongbox_opened   # set on solve; also makes the reward one-time
  give_item: brass_token            # first solve only (needs set_flag, or it repeats)
  dialogue: strongbox_opened        # knot barked when the world resumes, first solve only
```

### Adding an activity type

1. Write a definition POJO in `activities/<type>/config/` (public fields, an `OnCompleteConfig on_complete`).
2. Extend `BaseActivityScreen` (full-screen) or `BasePopupActivity` (popup). Load the definition in
   `launch(id)`, build the UI in `show()` / `buildBody(Table)`, call
   `pendingDialogue = onComplete.apply(activityId, definition.on_complete)` on solve, then
   `startWinPause(seconds)`.
3. Bind it in `WorldModule`: `@Provides @Singleton @IntoMap @StringKey("<type>") PopupActivity bind(X x)`
   (for a full-screen one, also bind it as a `Screen` under `@ScreenKey(X.class)`).
4. Create `activities/<type>/` in a pack and drop definitions in. The validators derive the set of
   known types from those directories.

## 8. Flags, items, quests, cutscenes

### 8.1 Flags

`FlagStore` is the single source of truth for "what has happened". Values are ints (a plain flag
is 1), so counters live here too. Namespaces: `act1.*` per act, `meta.*` persistent profile that
survives a new game. **Declare every flag** in a pack's `flags.yaml`; the validators fail on any
flag referenced anywhere (Ink literals, staging, quests, activities, TMX) that no pack declares.
A pack that only *reads* another pack's flag lists it under `requiresFlags:` in its `pack.yaml`.

### 8.2 Items

`packs/baseline/items/items.yaml`: `id`, `name`, `description`, `color`, `stackable`. Names and
descriptions may be `@keys` into `i18n/strings.properties`.

### 8.3 Quests

`quests/<id>.yaml`: ordered steps, each with a `flag`. The log shows a step complete when its flag
is set; `requires:` flags hide the quest until met; `complete_flag` short-circuits the whole quest.

### 8.4 Cutscenes

A `ScriptedEvent` (`begin(SceneDirector)` + `update(delta)` returning `true` while running) bound
under a string id in `WorldModule`. Fire it from a trigger's `event:` or an Ink `# event: <id>` tag.
The sample `fade_beat` dims the screen, holds, and lifts; use `director.setFade(alpha)` for the veil
and the ECS engine for anything that moves.

## 9. Art

- **Tiles**: 16x16 PNG + `.tsx`, loaded by `TmxMapLoader` (never through the atlas pipeline).
- **Characters**: authored in Aseprite with tags `<state>_<direction>` (`idle`/`walk` x
  `down`/`right`/`up`; `left` is `right` flipped), packed into one atlas per pack by
  `./gradlew buildAtlases -Ppack=<id>`, and described by `sprites/<id>.sprite.yaml`
  (`atlas`, `region`, frame size, `frameDuration`). `CharacterSpriteContentTest` checks every
  region the loader will ask for exists in the atlas.
- **Placeholders**: anything without art renders as a colour swatch (`color:` fields). Keep the
  `color:` next to a `sprite:` as the fallback.
- **Pipeline from scratch**: `genPlaceholderArt` (PNG sheets) -> `genCharacterAseprites`
  (`.aseprite`) -> `buildAtlases -Ppack=baseline` (`.atlas` + `.png`). Set `aseprite.bin` in
  `local.properties`. Commit the outputs; CI never runs Aseprite.

## 10. Validate, build, ship

```bash
./gradlew validatePacks                  # ContentValidation, StagingValidation, InkContentValidation,
                                         # PackConsistency, TilesetValidation, CharacterSpriteContent
./gradlew shipPack -Ppack=act2           # generatePackManifests -> compileInk -> validate -> zip
./gradlew bundleDlc -Pdlc=road-pack -Ppacks=act2
```

`assets/packs/` and `assets/assets.txt` are regenerated by every build; never hand-edit them.
`pack.yaml`'s `files:` list is regenerated by `generatePackManifests`; author the metadata above it.

## 11. YAML reference

### pack.yaml

```yaml
schemaVersion: 1
id: act1                    # == folder name
kind: act                   # act | epilogue | baseline
version: 1                  # bump to force re-extraction on players' machines
bundled: true               # ships inside the app; false = DLC zip
requiresBaseline: true
baselineVersion: ">=1"
provides:
  - id: act1
    title: The Gatehouse
    order: 1                # the act spine is sorted by this
    entryMap: gatehouse_yard
    entrySpawn: start
    gateFlag: null          # optional flag that marks this act's gate as crossed
flags: [act1.met_keeper]    # mirror of flags.yaml (convention)
requiresFlags: []           # flags declared by other packs that this pack reads
files:                      # GENERATED
```

### manifest.yaml (baseline)

```yaml
acts:
  - { id: act1, title: The Gatehouse, order: 1, source: BUNDLED, entitlement: FREE }
  - { id: act2, title: The Road,      order: 2, source: DLC,     entitlement: PAID }
dlc:
  - { id: road-pack, entitlement: PAID, packs: [act2] }
```

### actor

```yaml
id: keeper
name: Keeper          # literal or @i18n key
sprite: npc           # optional sprites/<sprite>.sprite.yaml
color: C06A2E         # swatch fallback
```

### match3

```yaml
id: hedge
title: Clear the hedge
board: { width: 6, height: 6, pieces: [bramble, { name: leaf, color: 8FA37E }, { name: thorn, atlas: atlases/act1.atlas, region: thorn }] }
win: { per_type: { bramble: 6, leaf: 6, thorn: 6 } }
on_complete: { set_flag: act1.hedge_cleared, dialogue: hedge_cleared }
```

### merge

```yaml
id: token_forge
title: Forge the sigil
hint: Drag two matching tokens together.
width: 4
height: 4
ladder:
  - { id: token_1, color: C9A24B, tier: 1 }
  - { id: token_2, color: E0B84A, tier: 2, from: [token_1, token_1] }
  - { id: sigil,   color: B23A48, tier: 3, from: [token_2, token_2] }
starting_inventory: { token_1: 4 }
win: { produce: sigil, count: 1 }
on_complete: { give_item: sigil, set_flag: act1.forged_sigil, dialogue: sigil_forged }
```

### lightsout

```yaml
id: strongbox
title: The strongbox latch
hint: Tap a plate to flip it and its neighbours.
board: { width: 3, height: 3 }
scramble: 4
lit_color: E8C860
unlit_color: 2A2E38
on_complete: { give_item: brass_token, set_flag: act1.strongbox_opened, dialogue: strongbox_opened }
```

### quest

```yaml
id: first_errand
title: "@quest.first_errand.title"
requires: []                # flags that must be set before the quest shows
complete_flag: null         # optional: marks the whole quest done
steps:
  - { id: meet, text: "Find the keeper.", flag: act1.met_keeper }
```

### sprite descriptor

```yaml
atlas: atlases/baseline.atlas
region: player              # regions <region>_<state>_<direction>
frameWidth: 48
frameHeight: 48
frameDuration: 0.12
flipRightForLeft: true
```
