INCLUDE ../../baseline/ink/common/bridge.ink

-> author_menu

// Act 2: "The Road". A DLC act reads base-game state through the bridge, never through Ink
// variables, so it runs standalone with whatever the base game produced.
=== traveller ===
~ set_flag("act2.met_traveller")
{ has_flag("act1.act_complete"):
    You came through the gatehouse. Then you know what the sigil is for. # speaker: Traveller
- else:
    A stranger on the road, and no sigil. Odd. # speaker: Traveller
}
-> END

=== author_menu ===
+ [traveller] -> traveller
