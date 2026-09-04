package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class NpcComponent implements Component, Pool.Poolable {
    public String dialogueId;

    @Override
    public void reset() {
        dialogueId = null;
    }
}
