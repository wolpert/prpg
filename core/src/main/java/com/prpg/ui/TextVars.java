package com.prpg.ui;

import com.prpg.narrative.StoryVariables;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Substitutes {@code {name}} placeholders in player-facing text with the story variable of that
 * name, so text that doesn't come from Ink (an i18n string, a quest step) can show live story state:
 * {@code "It is day {day}."}. Unknown names are left intact. Applied at the display boundary, after
 * localization resolution by {@link Strings}. (Ink lines interpolate {@code {day}} themselves.)
 */
@Singleton
public class TextVars {

    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private final StoryVariables variables;

    @Inject
    public TextVars(StoryVariables variables) {
        this.variables = variables;
    }

    public String apply(String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = variables.get(m.group(1));
            String replacement = value != null ? String.valueOf(value) : m.group(0);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
