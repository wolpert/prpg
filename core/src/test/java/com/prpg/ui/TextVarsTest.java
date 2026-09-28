package com.prpg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.prpg.narrative.StoryVariables;
import org.junit.jupiter.api.Test;

class TextVarsTest {

    @Test
    void tokensBecomeStoryVariablesAndUnknownOnesStay() {
        StoryVariables vars = new StoryVariables();
        vars.set("day", 3);
        vars.set("name", "Ash $1");
        TextVars text = new TextVars(vars);
        assertEquals("Day 3, Ash $1. {missing}", text.apply("Day {day}, {name}. {missing}"));
        assertEquals("no tokens", text.apply("no tokens"));
    }
}
