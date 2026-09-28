// Shared Ink <-> Java bridge. INCLUDEd by every act's story.
//
// Story state needs no bridge: it is plain Ink variables (VAR), which the engine saves and shares
// between acts by name. The functions below cover only what Ink can't hold itself.
//
// Every EXTERNAL declared here MUST be bound by StateBridge (core/.../narrative/StateBridge.java);
// InkContentValidationTest asserts the two sets match both ways.

// --- reads (bound lookahead-safe in Java; pure, no side effects) ---
EXTERNAL has_item(id)
EXTERNAL item_count(id)
EXTERNAL act_unlocked(id)
EXTERNAL owns_act(id)
EXTERNAL current_act()
// Why the next act can or can't be entered right now: "ADVANCED" (it can), "BLOCKED_NARRATIVE",
// "BLOCKED_NOT_OWNED", "BLOCKED_NOT_INSTALLED", or "COMPLETE" (no further act).
EXTERNAL next_act_gate()

// --- writes (bound NOT lookahead-safe in Java, so a side effect never double-fires) ---
EXTERNAL give_item(id)
EXTERNAL take_item(id)
EXTERNAL unlock_act(id)
// Move to the next act if its gates allow (check next_act_gate() first to explain a refusal).
EXTERNAL advance_act()

// --- describing the world ---
// Call these only inside the functions the engine evaluates:
//   === function cast() ===     define(id, sprite, color)        what an id looks like
//   === function stage() ===    actor(id, map, marker, -> knot)  a character; talking runs the knot
//                               thing(id, map, marker, -> knot, solid)
//                                                                an object; solid blocks the way
//                               zone(id, map, marker, -> knot)   walking in runs the knot
//                               lock(portal)                     a map portal that doesn't work
//   === function journal() ===  quest(title)                     a journal heading
//                               step(text, done)                 a line under it, ticked when done
// Pass "" instead of -> knot for something the player can't interact with, and "" for no sprite.
EXTERNAL actor(id, map, marker, knot)
EXTERNAL thing(id, map, marker, knot, solid)
EXTERNAL zone(id, map, marker, knot)
EXTERNAL lock(portal)
EXTERNAL define(id, sprite, color)
EXTERNAL quest(title)
EXTERNAL step(text, done)

// --- commands -------------------------------------------------------------------------------
// A line starting >>> is an instruction for the engine, not text for the player. The story waits
// on it, then carries on:
//   >>> play <type> <id>      play activities/<type>/<id>.yaml; then read activity_won
//   >>> go <map> [<spawn>]    move the player
//   >>> wait <seconds>        a pause
//   >>> fade out|in [<secs>]  darken or lift the screen
//   >>> cutscene <id>         run a Java ScriptedEvent
// Inky just shows these lines as text, which keeps a preview readable.

// --- Inky / inklecate fallbacks -------------------------------------------------------
// These let a story run in Inky's preview player, which has no Java host. Ink calls a fallback
// only when the matching EXTERNAL is UNBOUND, so in-game the StateBridge bindings always win.
// Reads return neutral defaults; to preview a gated branch, temporarily change a return value.
// The world functions print what they would do, so `~ stage()` in a preview knot lists the stage.
=== function has_item(id) ===
~ return false
=== function item_count(id) ===
~ return 0
=== function act_unlocked(id) ===
~ return false
=== function owns_act(id) ===
~ return false
=== function current_act() ===
~ return "act1"
=== function next_act_gate() ===
~ return "BLOCKED_NARRATIVE"
=== function give_item(id) ===
[+ {id}]
=== function take_item(id) ===
[- {id}]
=== function unlock_act(id) ===
[unlocked {id}]
=== function advance_act() ===
[advance to the next act]
=== function actor(id, map, marker, knot) ===
[actor {id} at {map}/{marker}]
=== function thing(id, map, marker, knot, solid) ===
[thing {id} at {map}/{marker}{solid: (solid)}]
=== function zone(id, map, marker, knot) ===
[zone {id} at {map}/{marker}]
=== function lock(portal) ===
[portal {portal} locked]
=== function define(id, sprite, color) ===
~ return
=== function quest(title) ===
[quest: {title}]
=== function step(text, done) ===
[{done:x|-}] {text}
