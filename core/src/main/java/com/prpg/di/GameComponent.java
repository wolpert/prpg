package com.prpg.di;

import com.badlogic.gdx.Game;
import com.prpg.TheGame;
import com.prpg.content.PackMounter;
import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = {
        CoreModule.class,
        com.prpg.world.WorldModule.class,
        com.prpg.narrative.NarrativeModule.class
})
public interface GameComponent {
    void inject(TheGame game);

    /**
     * Resolves the pack mounter so {@code TheGame} can extract/mount content packs <em>before</em>
     * {@link #inject} resolves content-backed singletons (Skin, Fonts, GameConfig) that read through
     * the content root.
     */
    PackMounter packMounter();

    @Component.Builder
    interface Builder {
        @BindsInstance Builder game(Game game);
        GameComponent build();
    }
}
