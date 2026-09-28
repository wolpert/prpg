# entry: gatehouse_yard start
INCLUDE ../../baseline/ink/common/bridge.ink
INCLUDE ../../baseline/ink/common/world.ink

// =====================================================================================
// Act 1: "The Gatehouse". This file IS the act: its state, its cast, who stands where, the
// journal, every conversation and every puzzle outcome. The engine reads it; nothing else in the
// pack says what happens.
//
// The `# entry:` tag on line 1 says where the act begins (map, then spawn). It has to be the very
// first line: Ink only treats a tag as a whole-story tag when nothing, not even an INCLUDE, precedes it.
// =====================================================================================

// Author preview entry point (Inky only). In-game, the engine diverts straight into a knot by name,
// so this top-level flow never runs during play.
-> author_menu

// --- state -----------------------------------------------------------------------------
VAR met_keeper = false
VAR hedge_cleared = false
VAR strongbox_opened = false
VAR forged_sigil = false
VAR took_ration = false
VAR crossed_gate = false
VAR rested = false

// --- what things look like --------------------------------------------------------------
=== function cast() ===
~ define("keeper", "npc", "C06A2E")
~ define("hedge", "", "2E5A24")
{ strongbox_opened:
    ~ define("strongbox", "", "4A3A28")
- else:
    ~ define("strongbox", "", "6E4A2A")
}
~ define("workbench", "", "9A6E44")
~ define("bench", "", "8A6038")
~ define("notice_board", "", "C9A26A")

// --- who and what is where, given the story so far --------------------------------------
=== function stage() ===
{ hedge_cleared:
    ~ actor("keeper", "gatehouse_hall", "keeper_desk", -> keeper_inside)
- else:
    ~ actor("keeper", "gatehouse_yard", "keeper_post", -> keeper_greeting)
    ~ thing("hedge", "gatehouse_yard", "hall_door", -> hedge, true)
}
~ thing("strongbox", "gatehouse_hall", "strongbox", -> strongbox, false)
{ strongbox_opened and not forged_sigil:
    ~ thing("workbench", "gatehouse_hall", "workbench", -> workbench, false)
}
{ not took_ration:
    ~ thing("travel_ration", "gatehouse_hall", "table", -> take_ration, false)
}
~ thing("bench", "gatehouse_yard", "bench", -> bench_rest, false)
~ thing("notice_board", "gatehouse_hall", "notice_board", -> notice_board, false)
{ met_keeper and not crossed_gate:
    ~ zone("gate_arch", "gatehouse_yard", "gate_arch", -> gate_arch)
}

// --- the journal --------------------------------------------------------------------------
=== function journal() ===
~ quest("@quest.first_errand.title")
~ step("Find the keeper in the yard.", met_keeper)
~ step("Clear the hedge from the hall door.", hedge_cleared)
~ step("Open the strongbox in the hall.", strongbox_opened)
~ step("Forge the tokens into a sigil.", forged_sigil)
~ step("Bring the sigil to the keeper.", act1_complete)

// --- the opening scene: runs each time the act is entered ----------------------------------
=== act_start ===
>>> fade out 0
>>> wait 0.4
>>> fade in 1
The gatehouse. Someone here is expecting a new hand. # speaker: (you)
-> END

// --- conversations ------------------------------------------------------------------------
// `# speaker:` names who talks. Sticky choices (+) stay available across repeat conversations.

=== keeper_greeting ===
~ met_keeper = true
You're the new hand? The gatehouse hall is behind that hedge. Clear it and come find me inside. # speaker: Keeper
+ [How do I clear it?]
    Pull three of a kind and it comes away in your hands. Tedious, but it works. # speaker: Keeper
    -> END
+ [On my way.] -> END

=== keeper_inside ===
{ not strongbox_opened:
    The strongbox by the wall. Open it; the latch is a puzzle, not a lock. # speaker: Keeper
    -> END
}
{ not has_item("sigil"):
    Take that token to the workbench and forge it into a sigil. Two of a kind, twice over. # speaker: Keeper
    -> END
}
~ act1_complete = true
~ unlock_act("act2")
A sigil. Then you're ready for the road. # speaker: Keeper
{ next_act_gate():
    - "ADVANCED":
        ~ advance_act()
        Go on, then. The gate is open. # speaker: Keeper
    - "BLOCKED_NOT_OWNED":
        The road beyond is another story, and it isn't yours yet. (Act 2 is not owned.) # speaker: Keeper
    - "BLOCKED_NOT_INSTALLED":
        The road beyond isn't built yet. (Act 2 is not installed; ship its pack and drop it into the content root.) # speaker: Keeper
    - else:
        The road beyond is closed for now. # speaker: Keeper
}
-> END

// --- puzzles: the knot plays the activity and decides what winning means --------------------

=== hedge ===
>>> play match3 hedge
{ activity_won:
    ~ hedge_cleared = true
    The last of the bramble comes away. The hall door stands clear. # speaker: (you)
}
-> END

=== strongbox ===
{ strongbox_opened:
    An open strongbox. Nothing left in it but dust. # speaker: (you)
    -> END
}
>>> play lightsout strongbox
{ activity_won:
    ~ strongbox_opened = true
    ~ give_item("brass_token")
    The latch clicks. Inside: brass tokens, stamped with the gatehouse mark. # speaker: (you)
}
-> END

=== workbench ===
>>> play merge token_forge
{ activity_won:
    ~ forged_sigil = true
    ~ give_item("sigil")
    The tokens fold into one seal. It feels heavier than four tokens should. # speaker: (you)
}
-> END

// --- things -------------------------------------------------------------------------------

=== take_ration ===
~ took_ration = true
~ give_item("travel_ration")
A travel ration. Hard bread, harder cheese. You pocket it. # speaker: (you)
-> END

=== bench_rest ===
A bench in the sun. It is day {day}.
+ [Rest a while.]
    ~ day++
    ~ rested = true
    You doze. When you wake it is day {day}. # speaker: (you)
    -> END
+ [Keep moving.] -> END

=== notice_board ===
A notice board. "Hands wanted. Ask the keeper." Someone has underlined "keeper" twice. # speaker: (you)
-> END

// Walking through the arch after meeting the keeper: once (stage() stops placing the zone).
=== gate_arch ===
~ crossed_gate = true
>>> cutscene fade_beat
-> END

// --- Inky-only menu so the preview player can pick a scene ---------------------------------
=== author_menu ===
+ [act_start] -> act_start
+ [keeper_greeting] -> keeper_greeting
+ [keeper_inside] -> keeper_inside
+ [hedge] -> hedge
+ [strongbox] -> strongbox
+ [workbench] -> workbench
+ [take_ration] -> take_ration
+ [bench_rest] -> bench_rest
+ [notice_board] -> notice_board
+ [gate_arch] -> gate_arch
+ [(what is staged right now)] -> preview_world

// Lists what stage() and journal() would produce with the preview's current variable values.
=== preview_world ===
~ stage()
~ journal()
-> author_menu
