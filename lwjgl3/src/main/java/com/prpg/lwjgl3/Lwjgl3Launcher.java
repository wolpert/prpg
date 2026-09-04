package com.prpg.lwjgl3;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.prpg.AppInfo;
import com.prpg.TheGame;
import org.lwjgl.glfw.GLFW;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {

    /** JVM system property that switches the log level to DEBUG: {@code -Dprpg.debug=true}. */
    private static final String DEBUG_PROPERTY = AppInfo.NAME + ".debug";

    /**
     * Default window size. The template targets a portrait phone shape (about 20:9, a 1080x2400
     * screen scaled down 3x). For a landscape game change this and the Android manifest's
     * {@code screenOrientation}, and widen {@code world.viewWidth/viewHeight} in {@code config/game.yaml}.
     */
    private static final int WINDOW_WIDTH = 540;
    private static final int WINDOW_HEIGHT = 1200;

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // macOS support; helps on Windows.
        // Force GLFW to use X11 (XWayland) on Linux when X11 is available. libGDX's Lwjgl3Graphics
        // calls glfwGetWindowPos, which Wayland does not support. The init hint must be set before
        // glfwInit() runs inside Lwjgl3Application's constructor.
        if (System.getProperty("os.name", "").toLowerCase().contains("linux")
                && GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_X11)) {
            GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
        }
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        Lwjgl3Application app = new Lwjgl3Application(new TheGame(), getDefaultConfiguration());
        // Default LOG_INFO (release: errors + info milestones); LOG_DEBUG when -D<app>.debug=true.
        app.setLogLevel(Boolean.getBoolean(DEBUG_PROPERTY) ? Application.LOG_DEBUG : Application.LOG_INFO);
        return app;
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(AppInfo.NAME);
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        // Icons live in lwjgl3/src/main/resources/.
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
