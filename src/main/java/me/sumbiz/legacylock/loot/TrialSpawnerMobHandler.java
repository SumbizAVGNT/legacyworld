package me.sumbiz.legacylock.loot;

import me.sumbiz.legacylock.hook.MythicMobsHook;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Map;
import java.util.function.Supplier;

public final class TrialSpawnerMobHandler implements Listener {

    private final MythicMobsHook mythicHook;
    private final Supplier<LootConfig> configSupplier;

    public TrialSpawnerMobHandler(MythicMobsHook mythicHook, Supplier<LootConfig> configSupplier) {
        this.mythicHook = mythicHook;
        this.configSupplier = configSupplier;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) return;

        LootConfig config = configSupplier.get();
        if (!config.isMobReplacementEnabled()) return;

        EntityType vanillaType = event.getEntityType();
        String mythicMobId = config.getMobReplacement(vanillaType);
        if (mythicMobId == null) return;

        Location spawnLoc = event.getEntity().getLocation();

        event.setCancelled(true);

        mythicHook.spawnMythicMob(mythicMobId, spawnLoc);
    }
}
