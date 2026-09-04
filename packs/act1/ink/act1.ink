INCLUDE ../../baseline/ink/common/bridge.ink

// Author preview entry point (Inky only). In-game, dialogue diverts straight into a knot by name
// (an NPC's dialogue == a knot), so this top-level flow never runs during play.
-> author_menu

// =====================================================================================
// Act 1: "The Gatehouse". One knot per talking thing. `# speaker:` is a tag the overlay reads;
// side effects and gates go through the bridge (set_flag / has_flag / give_item / ...) so Java
// stays canonical. Sticky choices (+) stay available across repeat conversations.
// =====================================================================================

=== keeper_greeting ===
~ set_flag("act1.met_keeper")
{ has_flag("act1.hedge_cleared"):
    The hedge is down. Go on in; the strongbox in the hall has something for you. # speaker: Keeper
    -> END
}
You're the new hand? The gatehouse hall is behind that hedge. Clear it and come find me inside. # speaker: Keeper
+ [How do I clear it?]
    Pull three of a kind and it comes away in your hands. Tedious, but it works. # speaker: Keeper
    -> END
+ [On my way.] -> END

=== keeper_inside ===
{ not has_item("brass_token") and not has_flag("act1.forged_sigil"):
    The strongbox by the wall. Open it; the latch is a puzzle, not a lock. # speaker: Keeper
    -> END
}
{ not has_item("sigil"):
    Take that token to the workbench and forge it into a sigil. Two of a kind, twice over. # speaker: Keeper
    -> END
}
~ set_flag("act1.act_complete")
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

=== hedge_cleared ===
The last of the bramble comes away. The hall door stands clear. # speaker: (you)
-> END

=== strongbox_opened ===
The latch clicks. Inside: brass tokens, stamped with the gatehouse mark. # speaker: (you)
-> END

=== strongbox_empty ===
An open strongbox. Nothing left in it but dust. # speaker: (you)
-> END

=== sigil_forged ===
The tokens fold into one seal. It feels heavier than four tokens should. # speaker: (you)
-> END

=== bench_rest ===
A bench in the sun. It is day {day()}.
+ [Rest a while.]
    ~ advance_day()
    ~ set_flag("act1.rested")
    You doze. When you wake it is day {day()}. # speaker: (you)
    -> END
+ [Keep moving.] -> END

=== notice_board ===
A notice board. "Hands wanted. Ask the keeper." Someone has underlined "keeper" twice. # speaker: (you)
-> END

// --- Inky-only menu so the preview player can pick a conversation -------------------
=== author_menu ===
+ [keeper_greeting] -> keeper_greeting
+ [keeper_inside] -> keeper_inside
+ [bench_rest] -> bench_rest
+ [notice_board] -> notice_board
