# entry: road start
INCLUDE ../../baseline/ink/common/bridge.ink
INCLUDE ../../baseline/ink/common/world.ink

// Act 2: "The Road". A DLC act: it reads what the base game did through the shared variables in
// world.ink (act1_complete), so it runs standalone with whatever state the player brings.

-> author_menu

VAR met_traveller = false

=== function cast() ===
~ define("traveller", "", "7A6E9B")

=== function stage() ===
~ actor("traveller", "road", "traveller_spot", -> traveller)

=== function journal() ===
~ quest("The road")
~ step("Speak to the traveller.", met_traveller)

=== traveller ===
~ met_traveller = true
{ act1_complete:
    You came through the gatehouse. Then you know what the sigil is for. # speaker: Traveller
- else:
    A stranger on the road, and no sigil. Odd. # speaker: Traveller
}
-> END

=== author_menu ===
+ [traveller] -> traveller
+ [(what is staged right now)] -> preview_world

=== preview_world ===
~ stage()
~ journal()
-> author_menu
