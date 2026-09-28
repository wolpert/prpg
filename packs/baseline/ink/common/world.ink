// Story state shared across acts. INCLUDEd by every act's story, after bridge.ink.
//
// Every Ink global variable is saved by name and handed to any act that declares it, so a variable
// two acts both read belongs here, declared once. A variable only one act uses belongs in that act's
// own .ink. The content validators fail if the same name is declared in two files.
//
// Names starting meta_ survive a new game (profile state); everything else resets.

// The in-fiction day.
VAR day = 1

// Set by the engine after every `>>> play`: did the player win? It starts true only so Inky's
// preview (which can't play anything) takes the winning branch.
VAR activity_won = true

// Act 1 is done: the keeper accepted the sigil. Act 2 reads it.
VAR act1_complete = false
