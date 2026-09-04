package com.prpg.di;

import com.badlogic.gdx.Screen;
import dagger.MapKey;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Dagger map-key for the screen registry. Each screen contributes itself into
 * {@code Map<Class<? extends Screen>, Provider<Screen>>} via
 * {@code @Provides @IntoMap @ScreenKey(SomeScreen.class)} so {@link
 * com.prpg.screens.ScreenNavigator#goTo(Class)} can dispatch by class without a
 * matching field/flag/dispose-line being added per screen.
 *
 * <p>The key is a {@code Class<? extends Screen>} so contributing modules choose any stable class
 * literal — typically the screen impl itself.
 */
@MapKey
@Retention(RetentionPolicy.RUNTIME)
public @interface ScreenKey {
    Class<? extends Screen> value();
}
