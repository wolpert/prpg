package com.prpg.ui;

import com.badlogic.gdx.files.FileHandle;
import com.prpg.content.ContentResolver;
import com.prpg.util.Log;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Localization seam. Player-facing strings in content may be either a literal (used verbatim) or
 * a reference of the form {@code @key} resolved against {@code assets/i18n/strings.properties}.
 * Act I prose is still inline (literals pass straight through), so this establishes the mechanism
 * cheaply now; content can migrate to keyed strings incrementally, and adding a locale later means
 * swapping the bundle rather than rewriting every YAML file.
 */
@Singleton
public class Strings {

    private static final String BUNDLE = "i18n/strings.properties";

    private final ContentResolver content;
    private Map<String, String> values;

    @Inject
    public Strings(ContentResolver content) {
        this.content = content;
    }

    /** Test seam: supply the lookup table directly, bypassing the content resolver. */
    Strings(Map<String, String> values) {
        this.content = null;
        this.values = values;
    }

    /**
     * Returns {@code text} unchanged unless it is a {@code @key} reference, in which case the
     * bundle value is returned (falling back to the raw {@code @key} so a missing key is visible).
     */
    public String resolve(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '@') return text;
        ensureLoaded();
        String value = values.get(text.substring(1));
        if (value != null) return value;
        Log.debug("Strings", "no value for key \"" + text.substring(1) + "\" in " + BUNDLE + " (returning raw token)");
        return text;
    }

    private void ensureLoaded() {
        if (values != null) return;
        values = new HashMap<>();
        FileHandle file = content.resolve(BUNDLE);
        if (!file.exists()) {
            Log.error("Strings", "i18n bundle " + BUNDLE + " not found; all @key references will render as raw tokens");
            return;
        }
        Properties props = new Properties();
        try (Reader r = file.reader("UTF-8")) {
            props.load(r);
        } catch (Exception e) {
            Log.error("Strings", "failed to load " + BUNDLE, e);
            return;
        }
        for (String name : props.stringPropertyNames()) {
            values.put(name, props.getProperty(name));
        }
    }
}
