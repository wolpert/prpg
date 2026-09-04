package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class PortalComponent implements Component, Pool.Poolable {
    public String targetMap;
    public String targetSpawn;
    public String requireFlag;

    @Override
    public void reset() {
        targetMap = null;
        targetSpawn = null;
        requireFlag = null;
    }
}
