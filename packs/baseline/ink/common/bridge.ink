// Shared Ink <-> Java bridge. INCLUDEd by every act's story.
//
// Every EXTERNAL declared here MUST be bound by StateBridge (core/.../narrative/StateBridge.java);
// InkContentValidationTest asserts the two sets match both ways. Java is the single source of truth:
// a story reads and writes durable state only through these functions.

// --- reads (bound lookahead-safe in Java; pure, no side effects) ---
EXTERNAL has_flag(name)
EXTERNAL flag_value(name)
EXTERNAL has_item(id)
EXTERNAL item_count(id)
EXTERNAL act_unlocked(id)
EXTERNAL owns_act(id)
EXTERNAL current_act()
// Why the next act can or can't be entered right now: "ADVANCED" (it can), "BLOCKED_NARRATIVE",
// "BLOCKED_NOT_OWNED", "BLOCKED_NOT_INSTALLED", or "COMPLETE" (no further act).
EXTERNAL next_act_gate()
EXTERNAL day()
// Is this actor/obstacle staged on that map right now?
EXTERNAL staged_on(id, map)

// --- writes (bound NOT lookahead-safe in Java, so a side effect never double-fires) ---
EXTERNAL set_flag(name)
EXTERNAL set_flag_value(name, value)
EXTERNAL clear_flag(name)
EXTERNAL give_item(id)
EXTERNAL take_item(id)
EXTERNAL unlock_act(id)
// Move to the next act if its gates allow (check next_act_gate() first to explain a refusal).
EXTERNAL advance_act()
EXTERNAL advance_day()
// --- staging: moves a flag condition can't express (the act's staging file is the default) ---
EXTERNAL place_actor(id, map, marker)
EXTERNAL remove_actor(id)
EXTERNAL set_actor_dialogue(id, knot)
EXTERNAL set_solid(id, solid)

// --- Inky / inklecate fallbacks -------------------------------------------------------
// These let a story run in Inky's preview player, which has no Java host. Ink calls a fallback
// only when the matching EXTERNAL is UNBOUND, so in-game the StateBridge bindings always win.
// Reads return neutral defaults; to preview a gated branch, temporarily change a return value.
=== function has_flag(name) ===
~ return false
=== function flag_value(name) ===
~ return 0
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
=== function day() ===
~ return 1
=== function staged_on(id, map) ===
~ return false
=== function set_flag(name) ===
~ return
=== function set_flag_value(name, value) ===
~ return
=== function clear_flag(name) ===
~ return
=== function give_item(id) ===
~ return
=== function take_item(id) ===
~ return
=== function unlock_act(id) ===
~ return
=== function advance_act() ===
~ return
=== function advance_day() ===
~ return
=== function place_actor(id, map, marker) ===
~ return
=== function remove_actor(id) ===
~ return
=== function set_actor_dialogue(id, knot) ===
~ return
=== function set_solid(id, solid) ===
~ return
