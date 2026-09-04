package com.prpg.activities;

import com.badlogic.gdx.Screen;

/**
 * An activity that takes over the whole screen (the world screen is hidden until it returns via
 * {@code ScreenNavigator.goToWorld()}). Registered into {@code Map<String, FullScreenActivity>} in
 * {@code WorldModule} under its {@code type} key, and also into the screen registry under
 * {@link #screenKey()} so the launcher can navigate to it.
 */
public interface FullScreenActivity extends Activity, Screen {

    /** This screen's registry key, so the launcher can navigate to it via the screen map. */
    Class<? extends Screen> screenKey();
}
