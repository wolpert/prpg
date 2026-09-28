# Content guide

The contract between content and the engine. **An act is its Ink story.** Everything that happens
in an act (its state, who stands where, what the journal says, every conversation, what winning a
puzzle means, where the act begins) is written in one `.ink` file, and the engine reads it. The few
other files an act has are things Ink can't sensibly hold: maps (drawn in Tiled), activity tuning
(board sizes, piece lists) and art.

The only things that need Java are a new **activity type**, a new **story command**, a new
**cutscene effect**, and a new **bridge function** (each is one small class or method plus one
binding, shown below).

**Core principle:** *Ink declares and changes the story's state; the engine remembers it and acts
on it.* Story state is plain Ink variables. The engine saves them by name, hands them to whichever
act needs them, and re-derives the world and the journal from them. Nothing about the story is
stored anywhere else.

## Contents

1. [What lives where](#1-what-lives-where)
2. [An act's Ink, top to bottom](#2-an-acts-ink-top-to-bottom)
3. [The authoring loop](#3-the-authoring-loop)
4. [Recipes](#4-recipes)
5. [Reference: state, world functions, commands, tags](#5-reference-state-world-functions-commands-tags)
6. [Maps: geography only](#6-maps-geography-only)
7. [Activities](#7-activities)
8. [Extending the engine (Java)](#8-extending-the-engine-java)
9. [Art](#9-art)
10. [Packs, DLC, validate, ship](#10-packs-dlc-validate-ship)
11. [YAML reference](#11-yaml-reference)

---

## 1. What lives where

| You want to change... | Edit | Format |
| --- | --- | --- |
| What anyone says, what choices exist | the act's `ink/<act>.ink` | Ink knots |
| What the story remembers | the act's Ink (`VAR`), or `baseline/ink/common/world.ink` if acts share it | Ink variables |
| Who or what is where, and when | the act's Ink `stage()` function | Ink |
| What a character or thing looks like | the act's Ink `cast()` function (plus art, section 9) | Ink |
| The journal | the act's Ink `journal()` function | Ink |
| What solving a puzzle does | the Ink knot that plays it (`>>> play ...`) | Ink |
| Cutscenes, pauses, fades, moving the player | `>>>` command lines in a knot | Ink |
| Where the act begins | `# entry: <map> <spawn>` on line 1 of the act's Ink | Ink tag |
| A room's layout, walls, exits, named spots | `maps/<id>.tmx` | Tiled |
| A puzzle's board size, pieces, difficulty | `activities/<type>/<id>.yaml` | YAML |
| The item catalog (names, descriptions) | `baseline/items/items.yaml` | YAML |
| Act title/order, DLC grouping, pack version | `pack.yaml`, `baseline/narrative/manifest.yaml` | YAML |
| Engine tunables, UI strings, sprites | `baseline/config/game.yaml`, `i18n/`, `sprites/` | YAML / properties |

A pack is one folder under `packs/`: the shared `baseline`, or an **act**. A new act is scaffolded
with `./gradlew newPack -Ppack=act3 -Porder=3` (section 4.1).

## 2. An act's Ink, top to bottom

The sample act `packs/act1/ink/act1.ink` is a worked example of everything here. Its shape:

```ink
# entry: gatehouse_yard start                      // 1. where the act begins: MUST be line 1
INCLUDE ../../baseline/ink/common/bridge.ink        // 2. engine functions (+ Inky fallbacks)
INCLUDE ../../baseline/ink/common/world.ink         //    story state shared between acts

-> author_menu                                      // 3. Inky-only entry point (never runs in-game)

VAR met_keeper = false                              // 4. this act's state
VAR hedge_cleared = false

=== function cast() ===                             // 5. what each staged id looks like
~ define("keeper", "npc", "C06A2E")                 //    id, sprite ("" for none), fallback colour
~ define("hedge", "", "2E5A24")

=== function stage() ===                            // 6. who and what is where, right now
{ hedge_cleared:
    ~ actor("keeper", "gatehouse_hall", "keeper_desk", -> keeper_inside)
- else:
    ~ actor("keeper", "gatehouse_yard", "keeper_post", -> keeper_greeting)
    ~ thing("hedge", "gatehouse_yard", "hall_door", -> hedge, true)
}

=== function journal() ===                          // 7. the quest log
~ quest("@quest.first_errand.title")
~ step("Find the keeper in the yard.", met_keeper)
~ step("Clear the hedge from the hall door.", hedge_cleared)

=== act_start ===                                   // 8. optional opening scene
>>> fade out 0
>>> fade in 1
The gatehouse. Someone here is expecting a new hand. # speaker: (you)
-> END

=== keeper_greeting ===                             // 9. conversations and outcomes
~ met_keeper = true
You're the new hand? # speaker: Keeper
-> END

=== hedge ===
>>> play match3 hedge
{ activity_won:
    ~ hedge_cleared = true
    The last of the bramble comes away. # speaker: (you)
}
-> END
```

How the engine uses it:

- **`# entry:`** is a whole-story tag, and Ink only treats a tag that way when *nothing* precedes
  it, not even an `INCLUDE`. It names a map in this pack and a spawn on it (the map's first spawn
  if omitted). A new game starts at the lowest-ordered act's entry; advancing an act moves the
  player to the next act's entry.
- **`cast()`, `stage()` and `journal()`** are functions the engine *evaluates*: it runs them
  against the current state and collects the calls they make. They are re-evaluated whenever a
  variable changes, the act changes, or a conversation moves on, so the world always matches the
  story and nothing about placement needs saving. Write them as pure descriptions: no text, no
  variable changes.
- **Knots** run when the player interacts with what `stage()` placed (or walks into a zone). A knot
  ends with `-> END`.
- **`act_start`** (optional) runs every time the act is entered, after the world is built.

## 3. The authoring loop

1. **Write in Inky.** Open `packs/<act>/ink/<act>.ink` in [Inky](https://github.com/inkle/inky).
   It resolves the `INCLUDE`s by their explicit paths. The top-level `-> author_menu` lets you pick
   any scene. The bridge fallbacks make the story playable without the game: world functions print
   what they would do, so the menu's **(what is staged right now)** entry lists `stage()` and
   `journal()` for the current variable values. `>>>` lines show as text. `activity_won` starts
   `true`, so previews take the winning branch. Inventory reads return false; to preview an
   item-gated branch, temporarily change a fallback's return value in `bridge.ink`.
2. **Check it.** `./gradlew validatePacks` compiles every act and checks every name the story uses
   that the Ink compiler can't see (section 10).
3. **Play it.**
   ```bash
   rm -rf ~/.prpg/content       # or the game keeps the copy it extracted last time
   ./gradlew compileInk         # writes narrative/<act>.ink.json; commit it
   ./gradlew lwjgl3:run
   ```
   The in-game HUD lists every story variable currently set, which is the quickest way to see what
   the story thinks has happened.

## 4. Recipes

Each recipe is the complete set of edits. Most are one file.

### 4.1 A new act

```bash
./gradlew newPack -Ppack=act3 -Porder=3
```

This writes `packs/act3/`: `pack.yaml` (DLC by default), a stub map `maps/act3_start.tmx` (walled
field, spawn `start`, marker `centre`), and `ink/act3.ink` with the whole skeleton from section 2
(entry tag, one variable, `cast()`, `stage()` placing one character, `journal()`, `act_start`, and
the preview menu). It boots as is. Then:

1. Write the act in `ink/act3.ink`; lay out its maps in Tiled.
2. Set `title:` in `pack.yaml`, and `bundled: true` if it ships in the app.
3. To advertise it before it is installed, add it to `baseline/narrative/manifest.yaml`
   (`source: DLC`, `entitlement: PAID`) and a `dlc:` group.
4. Hand off from the previous act (4.9).
5. `./gradlew shipPack -Ppack=act3`. Commit `narrative/act3.ink.json` and `pack.yaml`.

### 4.2 A character who talks

```ink
=== function cast() ===
~ define("keeper", "npc", "C06A2E")          // sprites/npc.sprite.yaml; C06A2E if the art is missing

=== function stage() ===
~ actor("keeper", "gatehouse_yard", "keeper_post", -> keeper_greeting)

=== keeper_greeting ===
~ met_keeper = true
You're the new hand? # speaker: Keeper
-> END
```

`keeper_post` is a named object in the map's `markers` layer (section 6). To move them later, put
the call in a condition; if the same id is placed twice, the last call wins:

```ink
{ hedge_cleared:
    ~ actor("keeper", "gatehouse_hall", "keeper_desk", -> keeper_inside)
- else:
    ~ actor("keeper", "gatehouse_yard", "keeper_post", -> keeper_greeting)
}
```

To take them out of the world, stop placing them.

### 4.3 A puzzle, and what winning means

```ink
=== strongbox ===
{ strongbox_opened:
    An open strongbox. Nothing left in it but dust. # speaker: (you)
    -> END
}
>>> play lightsout strongbox
{ activity_won:
    ~ strongbox_opened = true
    ~ give_item("brass_token")
    The latch clicks. Inside: brass tokens. # speaker: (you)
}
-> END
```

`>>> play <type> <id>` opens `activities/<type>/<id>.yaml` (a full-screen screen or a popup over
the world, depending on the type), waits until the player finishes or backs out, and sets
`activity_won`. The reward, the variable and the line are all ordinary Ink, so "first time only",
"only if you have the key", or "a different line on a retry" are just conditions.

### 4.4 Something that blocks the way

```ink
=== function stage() ===
{ not hedge_cleared:
    ~ thing("hedge", "gatehouse_yard", "hall_door", -> hedge, true)   // true = solid
}
```

A solid thing blocks movement over its marker's rectangle. Once `hedge_cleared` is set, `stage()`
stops placing it: it stops rendering *and* stops blocking, without reloading the map. A thing that
appears on top of the player is inert until they step clear. Pass `""` instead of `-> knot` for
scenery the player can't interact with.

### 4.5 An item to pick up

```ink
=== function stage() ===
{ not took_ration:
    ~ thing("travel_ration", "gatehouse_hall", "table", -> take_ration, false)
}

=== take_ration ===
~ took_ration = true
~ give_item("travel_ration")
A travel ration. You pocket it. # speaker: (you)
-> END
```

A thing whose id is an item id (and has no `define()`) shows that item's icon. Items are declared
once in `baseline/items/items.yaml`.

### 4.6 A walk-in moment or cutscene

```ink
=== function stage() ===
{ met_keeper and not crossed_gate:
    ~ zone("gate_arch", "gatehouse_yard", "gate_arch", -> gate_arch)
}

=== gate_arch ===
~ crossed_gate = true
>>> cutscene fade_beat
-> END
```

A zone has no art (for now it shows as a faint tint) and runs its knot when the player walks
in. It fires every time they enter, so a one-off moment sets a variable that makes `stage()` stop
placing it. A knot made only of commands never shows the dialogue box.

### 4.7 A quest

```ink
=== function journal() ===
~ quest("@quest.first_errand.title")                    // an i18n key or a literal
~ step("Find the keeper in the yard.", met_keeper)
~ step("Clear the hedge from the hall door.", hedge_cleared)
{ act1_complete:                                        // a quest appears when the story says so
    ~ quest("The road")
    ~ step("Find out where it leads.", false)
}
```

A step is done when its second argument is true; the first step not done is the current one; a
quest is complete when every step is done. Any Ink expression works as the condition, and
`{name}` in the text shows a story variable's value. The journal shows the current act's quests.

### 4.8 Moving the player, pauses and fades

```ink
=== keeper_escort ===
Follow me. # speaker: Keeper
>>> fade out 0.5
>>> go gatehouse_hall from_yard
>>> wait 0.3
>>> fade in 0.5
Here we are. # speaker: Keeper
-> END
```

Map exits the player walks through are **portals** drawn in the map (section 6); to shut one for
a while, `~ lock("yard_door")` in `stage()` (by the portal's name). If a conversation ends while
faded out, the engine lifts the veil itself.

### 4.9 Ending an act

```ink
~ act1_complete = true
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

When the conversation ends, the world moves the player to the new act's `# entry:` (with an
autosave) and runs its `act_start`.

### 4.10 State two acts share

Declare it once in `baseline/ink/common/world.ink`; every act INCLUDEs that file:

```ink
VAR act1_complete = false     // set by act 1, read by act 2
```

Variables are shared **by name**: when an act's story starts, the engine hands it the saved value
of every variable it declares. That is what lets a DLC act (compiled and shipped separately) read
what the base game did. The validators fail if two files declare the same name, so sharing is
never accidental. Names starting `meta_` survive a new game (profile state).

## 5. Reference: state, world functions, commands, tags

### 5.1 State

- `VAR name = value` declares a variable (bool, int, float, string, or a `LIST` value). Change it
  with `~ name = value`, `~ name++`, and so on; read it in any condition or as `{name}` in text.
- The engine saves every variable by name and restores it into any story that declares it, so a
  story can be edited and old saves still load (a renamed variable simply starts at its default).
- Engine-provided (in `world.ink`): `day` (the in-fiction day; `~ day++` to advance it) and
  `activity_won` (written after every `>>> play`).

### 5.2 World functions

Declared in `bridge.ink`. Call them only inside the function named; elsewhere they are ignored
(and logged).

| Call | Inside | Meaning |
| --- | --- | --- |
| `define(id, sprite, color)` | `cast()` | What an id looks like. `sprite` names `sprites/<sprite>.sprite.yaml` (`""` for none); `color` is the `RRGGBB` swatch used when there is no art. May be conditional. |
| `actor(id, map, marker, -> knot)` | `stage()` | A character at a marker; interacting runs the knot. |
| `thing(id, map, marker, -> knot, solid)` | `stage()` | An object at a marker, sized to the marker. `solid` blocks movement. `""` for no knot. |
| `zone(id, map, marker, -> knot)` | `stage()` | An invisible area, sized to the marker; walking in runs the knot. |
| `lock(portal)` | `stage()` | The map portal with that name does nothing while locked. |
| `quest(title)` | `journal()` | Starts a journal heading. |
| `step(text, done)` | `journal()` | A line under the last heading, ticked when `done`. |

Maps and markers are strings (checked by `validatePacks`); knots are `-> knot` (checked by the Ink
compiler). If the same id is placed twice in one `stage()`, the last call wins.

### 5.3 Other bridge functions

| Function | Kind | Effect |
| --- | --- | --- |
| `has_item(id)` / `item_count(id)` | read | Inventory queries. |
| `give_item(id)` / `take_item(id)` | write | Inventory changes. |
| `act_unlocked(id)` / `owns_act(id)` / `current_act()` | read | Act spine queries. |
| `next_act_gate()` | read | `"ADVANCED"`, `"BLOCKED_NARRATIVE"`, `"BLOCKED_NOT_OWNED"`, `"BLOCKED_NOT_INSTALLED"` or `"COMPLETE"`. |
| `unlock_act(id)` / `advance_act()` | write | Narrative unlock; enter the next act if its gates allow. |

Reads are lookahead-safe; writes are not, so a side effect fires exactly once.

### 5.4 Commands

A line starting `>>>` is an instruction to the engine. The story stops on it, the dialogue box
hides, and the story continues when the command finishes.

| Command | Waits for |
| --- | --- |
| `>>> play <type> <id>` | the player to finish `activities/<type>/<id>.yaml`; then `activity_won` is true or false. |
| `>>> go <map> [<spawn>]` | the player to be moved (to the map's first spawn if none is named). |
| `>>> wait <seconds>` | the time to pass. |
| `>>> fade out [<seconds>]` / `>>> fade in [<seconds>]` | the screen to darken or clear (default 0.5 s; `0` is instant). |
| `>>> cutscene <id>` | a Java `ScriptedEvent` registered under `id` to finish (sample: `fade_beat`). |

`validatePacks` checks every command's name, argument count, and the activity, map and spawn it
names. An unknown command at runtime is logged and skipped.

### 5.5 Tags

| Tag | Where | Meaning |
| --- | --- | --- |
| `# entry: <map> [<spawn>]` | line 1 of the act's Ink | Where the act begins. |
| `# speaker: Name` | on a line | Who is speaking (a literal or an `@i18n` key). |

Other tags (`portrait`, `mood`, ...) are passed over for now; they are reserved for the art pass.

## 6. Maps: geography only

Author maps in Tiled via `art/prpg.tiled-project` (it points at `packs/` and declares the `Spawn`,
`Portal` and `Marker` classes). Orthogonal, **16x16** tiles, saved as `packs/<act>/maps/<id>.tmx`;
the filename without `.tmx` is the map id.

**A map holds only what doesn't change**: terrain, collision, where the player can appear, exits,
and named spots. Everything the story controls is placed by `stage()` at those spots, so the same
room can be populated differently as the story moves on, and a map can be re-laid-out without
touching the story (as long as the names stay).

### Tile layers

| Layer | Required | Purpose |
| --- | --- | --- |
| `ground` | yes | Base terrain. Sets the tile size and the map's pixel bounds. Drawn below entities. |
| `walls` | no | Decoration drawn with `ground`, below entities. Visual only. |
| `collision` | recommended | **Any non-empty cell blocks movement.** Not drawn. Out-of-bounds always blocks. |
| `overlay` | no | Drawn *above* entities (canopy, roof edges). |

### Object layers

| Layer | Class | What it is |
| --- | --- | --- |
| `spawn` | `Spawn` | Where the player's **feet** land. Named; `# entry:`, portals and `>>> go` refer to it. |
| `portals` | `Portal` | A named walk-in exit: `target_map`, `target_spawn`. The story can `lock()` it. |
| `markers` | `Marker` | Named spots `stage()` places things at. The rectangle is the placed thing's footprint. |

Any other object layer is ignored by the engine, so `validatePacks` fails if one holds objects.

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

## 7. Activities

An activity is a minigame with a **tuning file**, `activities/<type>/<id>.yaml` (the filename base
is the id), played by the story with `>>> play <type> <id>`. The file holds only how the puzzle
plays (board, pieces, win condition); what solving it means is in the knot that played it.

Two presentations, chosen by the Java class, invisible to the story:

- **Full-screen** (`FullScreenActivity`, base class `BaseActivityScreen`): replaces the world
  screen; Esc/Back returns. Samples: `match3`, `merge`.
- **Popup** (`PopupActivity`, base class `BasePopupActivity`): a panel over the frozen world; Esc,
  Back or Close dismiss it; a solve closes it after a short pause. Sample: `lightsout`.

Backing out counts as not winning (`activity_won` is false). The tuning formats are in section 11.

## 8. Extending the engine (Java)

### 8.1 A new activity type

1. Write a definition POJO in `activities/<type>/config/` (public fields).
2. Extend `BaseActivityScreen` (full-screen) or `BasePopupActivity` (popup). Load the definition
   in `launch(id)` with `loadDefinition(...)`, build the UI in `show()` / `buildBody(Table)`, and
   on a solve call `markWon()` then `startWinPause(seconds)`.
3. Bind it in `WorldModule`: `@Provides @Singleton @IntoMap @StringKey("<type>") PopupActivity bind(X x)`
   (for a full-screen one, bind it as a `FullScreenActivity` and as a `Screen` under `@ScreenKey(X.class)`).
4. Create `activities/<type>/` in a pack and add tuning files. The validators derive the set of
   known types from those directories.

### 8.2 A new story command

1. Implement `narrative/StoryCommand` in `world/command/`: `start(args)` begins it (return `false`
   if it can't run, and log why), `update(delta)` returns `true` while the story must wait.
2. Bind it in `WorldModule` under `@StringKey("<name>") StoryCommand`.
3. Add its `Spec` (name, argument counts, usage) to `world/command/Commands.ALL` so
   `validatePacks` recognises it, and document it in `bridge.ink`'s command list.

### 8.3 A new cutscene effect

A `ScriptedEvent` (`begin(SceneDirector)` + `update(delta)` returning `true` while running) bound
under a string id in `WorldModule`; the story runs it with `>>> cutscene <id>`. The sample
`fade_beat` dims the screen, holds, and lifts. Prefer the story's own commands (`fade`, `wait`,
`go`) when they are enough.

### 8.4 A new bridge function

Declare it with an Inky fallback in `bridge.ink`, add its name to the matching list in
`StateBridge` (`READ_FUNCTIONS`, `WRITE_FUNCTIONS` or `WORLD_FUNCTIONS`), and bind it.
`InkContentValidationTest` keeps the two sides in sync.

## 9. Art

- **Tiles**: 16x16 PNG + `.tsx`, loaded by `TmxMapLoader` (never through the atlas pipeline).
- **Characters**: authored in Aseprite with tags `<state>_<direction>` (`idle`/`walk` x
  `down`/`right`/`up`; `left` is `right` flipped), packed into one atlas per pack by
  `./gradlew buildAtlases -Ppack=<id>`, and described by `sprites/<id>.sprite.yaml`
  (`atlas`, `region`, frame size, `frameDuration`). A story uses one by naming it in `define()`.
  `CharacterSpriteContentTest` checks every region the loader will ask for exists in the atlas.
- **Placeholders**: anything without art renders as its `define()` colour. Keep a colour next to a
  sprite as the fallback.
- **Pipeline from scratch**: `genPlaceholderArt` (PNG sheets) -> `genCharacterAseprites`
  (`.aseprite`) -> `buildAtlases -Ppack=baseline` (`.atlas` + `.png`). Set `aseprite.bin` in
  `local.properties`. Commit the outputs; CI never runs Aseprite.

## 10. Packs, DLC, validate, ship

| Term | Meaning |
| --- | --- |
| **Pack** | One folder under `packs/`: an **act** or the shared **baseline**. The unit you *build*. |
| **Act** | A pack whose Ink story is a chapter of the game, plus the maps and tuning it uses. Acts are ordered; the player moves through them one way. |
| **DLC** | The unit you *sell*: one or more pack zips grouped in `manifest.yaml`'s `dlc:` section. |
| **baseline** | Shared by every act: ui, fonts, config, the item catalog, `bridge.ink` and `world.ink`, the act catalog, the player sprite, the placeholder tileset. No maps. |

- `bundled: true` in `pack.yaml` ships the pack inside the app. Every other pack is DLC:
  `./gradlew shipPack -Ppack=<id>` zips it, and dropping the zip (or the unzipped folder) into the
  content root installs it on next launch.
- **Content root**: desktop `~/.prpg/content/`, Android the app-private `content/` dir. Bundled
  packs are extracted there on first run and re-extracted only when `pack.yaml` `version:` rises.
- At runtime everything resolves through `ContentResolver`: mounted packs first (acts before
  baseline), then bundled assets, so an act can shadow a baseline file.

> **Entitlement is not enforced.** `DefaultEntitlement` owns anything catalogued `FREE` or
> mounted, plus any act with a `meta_owns_<act>` profile variable. That variable is the seam a
> real store writes through (`DefaultEntitlement.grant(actId)`); drop the "mounted == owned" rule
> when you wire one up.

### Validate

```bash
./gradlew validatePacks
```

| Guard rail | Checks |
| --- | --- |
| `InkContentValidationTest` | every act compiles; `bridge.ink` and `StateBridge` agree; every act plays in Inky's preview on the fallbacks; bundled acts have committed JSON |
| `InkWorldValidationTest` | `# entry:` exists and names a real map and spawn in the act's pack; every map, marker, portal, sprite, colour and item named in the Ink exists; every `>>>` command is known, well formed, and names a real activity, map and spawn; each act's opening `stage()` resolves onto real markers; no variable is declared in two files |
| `ContentValidationTest` | activity tuning is well formed and carries no story; portals lead to real maps and spawns; maps hold only geography |
| `PackConsistencyTest` | `pack.yaml` `files:` lists match disk; no retired content (`staging/`, `quests/`, `actors/`, `flags.yaml`, retired `pack.yaml` fields); atlas pages exist |
| `TilesetValidationTest`, `CharacterSpriteContentTest` | tilesets match their images; sprite regions exist in their atlases |

### Build and ship

```bash
./gradlew shipPack -Ppack=act2           # generatePackManifests -> compileInk -> validate -> zip
./gradlew bundleDlc -Pdlc=road-pack -Ppacks=act2
```

`assets/packs/` and `assets/assets.txt` are regenerated by every build; never hand-edit them.
`pack.yaml`'s `files:` list is regenerated by `generatePackManifests`; author the metadata above
it. Ink sources (`ink/`) are build-time only; the compiled `narrative/<act>.ink.json` is what ships.

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
    order: 1                # the act spine is sorted by this; where it begins is `# entry:` in its Ink
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

### items.yaml (baseline)

```yaml
items:
  - id: brass_token
    name: Brass Token                   # literal or @i18n key
    description: "A worn disc stamped with the gatehouse mark."
    color: C9A24B                       # icon swatch until real art lands
    stackable: true
```

### match3

```yaml
id: hedge
title: Clear the hedge
board: { width: 6, height: 6, pieces: [bramble, { name: leaf, color: 8FA37E }, { name: thorn, atlas: atlases/act1.atlas, region: thorn }] }
win: { per_type: { bramble: 6, leaf: 6, thorn: 6 } }
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
