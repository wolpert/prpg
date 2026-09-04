package com.prpg.debug;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.prpg.prefs.UserPreferences;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Heads-up perf overlay. Toggle via the Debug Overlay checkbox in Preferences.
 *
 * <p>Reports FPS, frame-time min/avg/max over the last {@value #HISTORY} frames, live entity count,
 * a free-form extras line, and cumulative GC count.
 */
@Singleton
public class DebugOverlay {

    private static final int HISTORY = 60;

    private final UserPreferences prefs;
    private final Engine engine;
    private final Supplier<String> extraLine;
    private final Skin skin;

    private final float[] frameTimes = new float[HISTORY];
    private int frameIdx;
    private int frameCount;

    private Stage stage;
    private Label label;

    @Inject
    public DebugOverlay(UserPreferences prefs, Engine engine, Supplier<String> extraLine, Skin skin) {
        this.prefs = prefs;
        this.engine = engine;
        this.extraLine = extraLine;
        this.skin = skin;
    }

    private void ensureStage() {
        if (stage != null) return;
        stage = new Stage();
        label = new Label("", skin);
        label.setColor(Color.LIME);
        Table table = new Table();
        table.setFillParent(true);
        table.top().right().pad(8);
        table.add(label).right();
        stage.addActor(table);
    }

    public void resize(int width, int height) {
        if (stage != null) stage.getViewport().update(width, height, true);
    }

    public void render(float delta) {
        if (!prefs.isDebugOverlayEnabled()) return;
        ensureStage();

        frameTimes[frameIdx] = delta;
        frameIdx = (frameIdx + 1) % HISTORY;
        if (frameCount < HISTORY) frameCount++;

        float min = Float.MAX_VALUE;
        float max = 0f;
        float sum = 0f;
        for (int i = 0; i < frameCount; i++) {
            float t = frameTimes[i];
            sum += t;
            if (t < min) min = t;
            if (t > max) max = t;
        }
        float avg = frameCount == 0 ? 0f : sum / frameCount;

        label.setText(String.format(
                "FPS: %d%nframe ms (min/avg/max): %.1f / %.1f / %.1f%nentities: %d%nextra: %s%nGC: %d",
                Gdx.graphics.getFramesPerSecond(),
                min * 1000f, avg * 1000f, max * 1000f,
                engine.getEntities().size(),
                extraLine.get(),
                gcCount()));

        stage.act(delta);
        stage.draw();
    }

    /**
     * Reflective probe of {@code java.lang.management.ManagementFactory} — Android's
     * platform stubs don't include the {@code java.lang.management} package, so a direct
     * reference fails R8's missing-class check on a release Android build. Returns -1 when
     * the API isn't reachable (Android, GraalVM image without management metadata, etc.).
     */
    private static long gcCount() {
        try {
            Class<?> factory = Class.forName("java.lang.management.ManagementFactory");
            Object beans = factory.getMethod("getGarbageCollectorMXBeans").invoke(null);
            if (!(beans instanceof List<?> list)) return -1;
            long total = 0;
            Method getCount = null;
            for (Object bean : list) {
                if (getCount == null) {
                    getCount = bean.getClass().getMethod("getCollectionCount");
                }
                long c = (Long) getCount.invoke(bean);
                if (c > 0) total += c;
            }
            return total;
        } catch (ReflectiveOperationException | LinkageError e) {
            return -1L;
        }
    }

    public void dispose() {
        if (stage != null) stage.dispose();
    }
}
