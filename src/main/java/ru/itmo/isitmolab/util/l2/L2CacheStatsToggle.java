package ru.itmo.isitmolab.util.l2;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class L2CacheStatsToggle {
    // JAVA_OPTS="-Dl2cache.stats.enabled=true"
    private final AtomicBoolean enabled =
            new AtomicBoolean(Boolean.getBoolean("l2cache.stats.enabled"));

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
