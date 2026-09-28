package com.prpg.activities;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.prpg.screens.ScreenNavigator;
import com.prpg.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Launches an activity by its {@code type} string without the world knowing concrete classes. Two
 * registries feed it, both multibound in {@code WorldModule}: full-screen activities (navigated to
 * as screens) and popup activities (opened over the world). A new activity type is one binding
 * into the matching map; nothing in {@code WorldScreen} changes.
 *
 * <p>The story is the only caller ({@code >>> play <type> <id>}, see {@code PlayCommand}): it launches
 * here, waits while {@link #isRunning()}, then reads {@link #wasWon()}.
 */
@Singleton
public class ActivityLauncher {

    private final Map<String, FullScreenActivity> screens;
    private final Map<String, PopupActivity> popups;
    private final Provider<ScreenNavigator> nav;

    private PopupActivity activePopup;
    /** The most recently launched activity, full-screen or popup. */
    private Activity current;

    @Inject
    public ActivityLauncher(Map<String, FullScreenActivity> screens, Map<String, PopupActivity> popups,
                            Provider<ScreenNavigator> nav) {
        this.screens = screens;
        this.popups = popups;
        this.nav = nav;
    }

    public boolean has(String type) {
        return type != null && (screens.containsKey(type) || popups.containsKey(type));
    }

    /** Prepares and starts the activity for {@code type}; returns false if unknown. */
    public boolean launch(String type, String activityId) {
        if (type == null || activityId == null) {
            Log.error("ActivityLauncher", "cannot launch activity: type=" + type + " id=" + activityId);
            return false;
        }
        PopupActivity popup = popups.get(type);
        if (popup != null) {
            if (activePopup != null && activePopup.isOpen()) return false;
            popup.launch(activityId);
            popup.open();
            activePopup = popup;
            current = popup;
            Log.debug("ActivityLauncher", "opened popup activity " + type + "/" + activityId);
            return true;
        }
        FullScreenActivity screen = screens.get(type);
        if (screen != null) {
            screen.launch(activityId);
            current = screen;
            nav.get().goTo(screen.screenKey());
            Log.debug("ActivityLauncher", "launched full-screen activity " + type + "/" + activityId);
            return true;
        }
        Log.error("ActivityLauncher", "unknown activity type \"" + type + "\" (id=" + activityId + ")");
        return false;
    }

    /** True while the most recently launched activity is still being played. */
    public boolean isRunning() {
        return current != null && current.isRunning();
    }

    /** Whether the most recently launched activity was solved (false if none ran). */
    public boolean wasWon() {
        return current != null && current.wasWon();
    }

    /** True while a popup activity is open over the world. */
    public boolean isPopupOpen() {
        return activePopup != null && activePopup.isOpen();
    }

    /** Closes the open popup, if any (the world's ESC/BACK path). */
    public void closePopup() {
        if (activePopup != null) activePopup.close();
    }

    /** Per-frame drive for the open popup; a no-op otherwise. */
    public void updatePopup(float delta) {
        if (activePopup != null) {
            activePopup.update(delta);
            if (!activePopup.isOpen()) activePopup = null;
        }
    }

    public void drawPopup() {
        if (activePopup != null && activePopup.isOpen()) activePopup.draw();
    }

    public void resize(int width, int height) {
        for (PopupActivity p : popups.values()) p.resize(width, height);
    }

    /** Every popup's stage, for the world's input multiplexer (a closed popup consumes nothing). */
    public List<Stage> popupStages() {
        List<Stage> out = new ArrayList<>();
        for (PopupActivity p : popups.values()) out.add(p.getStage());
        return out;
    }

    public void dispose() {
        for (PopupActivity p : popups.values()) p.dispose();
    }
}
