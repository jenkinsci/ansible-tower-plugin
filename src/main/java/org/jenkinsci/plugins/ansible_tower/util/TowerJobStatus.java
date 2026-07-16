package org.jenkinsci.plugins.ansible_tower.util;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TowerJobStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean completed;
    private final boolean failed;
    private final Map<String, String> artifacts;

    TowerJobStatus(boolean completed, boolean failed) {
        this(completed, failed, Collections.<String, String>emptyMap());
    }

    TowerJobStatus(boolean completed, boolean failed, Map<String, String> artifacts) {
        this.completed = completed;
        this.failed = failed;
        this.artifacts = Collections.unmodifiableMap(new HashMap<String, String>(artifacts));
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isFailed() {
        return failed;
    }

    public Map<String, String> getArtifacts() {
        return artifacts;
    }
}
