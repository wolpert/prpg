package com.prpg.world;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Tracks which fire-once triggers have already fired, so they stay spent across map reloads. */
@Singleton
public class TriggerHistory {

    private final Set<String> fired = new HashSet<>();

    @Inject
    public TriggerHistory() {}

    public boolean hasFired(String triggerId) {
        return triggerId != null && fired.contains(triggerId);
    }

    public void markFired(String triggerId) {
        if (triggerId != null) fired.add(triggerId);
    }

    public Set<String> getFired() {
        return fired;
    }

    public void loadAll(Set<String> ids) {
        fired.clear();
        fired.addAll(ids);
    }

    public void clear() {
        fired.clear();
    }
}
