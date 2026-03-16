package me.sumbiz.legacylock.loot;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrialSpawnerTracker {

    public record SpawnContext(boolean isOminous, long spawnTimeMillis) {}

    private final ConcurrentHashMap<UUID, SpawnContext> tracked = new ConcurrentHashMap<>();

    public void track(UUID entityUuid, boolean isOminous) {
        tracked.put(entityUuid, new SpawnContext(isOminous, System.currentTimeMillis()));
    }

    public SpawnContext remove(UUID entityUuid) {
        return tracked.remove(entityUuid);
    }

    public void cleanup(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        tracked.entrySet().removeIf(e -> e.getValue().spawnTimeMillis() < cutoff);
    }

    public int size() {
        return tracked.size();
    }
}
