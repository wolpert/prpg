package com.prpg.android;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.prpg.TheGame;

/** Launches the Android application. */
public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true; // Recommended, but not required.
        initialize(new TheGame(), configuration);
        // Debug builds log everything; release runs at LOG_INFO (errors + info milestones). Keyed off
        // the manifest debuggable flag so it needs no BuildConfig generation.
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        setLogLevel(debuggable ? Application.LOG_DEBUG : Application.LOG_INFO);
    }
}
