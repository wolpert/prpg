package com.prpg.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class TriggerComponent implements Component, Pool.Poolable {
    public String id;
    public String setFlag;
    public String requireFlag;
    public boolean fireOnce;
    /** Optional named event for special handling (e.g. "margaret_sighting"). */
    public String event;

    @Override
    public void reset() {
        id = null;
        setFlag = null;
        requireFlag = null;
        fireOnce = false;
        event = null;
    }
}
