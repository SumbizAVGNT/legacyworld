package me.sumbiz.legacylock.loot;

import me.sumbiz.legacylock.hook.MythicMobsHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class TrialLootHandler implements Listener {

    private final TrialSpawnerTracker tracker;
    private final Supplier<LootConfig> configSupplier;
    private final MythicMobsHook mythicHook;

    public TrialLootHandler(TrialSpawnerTracker tracker,
                            Supplier<LootConfig> configSupplier,
                            MythicMobsHook mythicHook) {
        this.tracker = tracker;
        this.configSupplier = configSupplier;
        this.mythicHook = mythicHook;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) return;

        LootConfig config = configSupplier.get();
        if (!config.isEnabled()) return;

        boolean ominous = detectOminous(event.getEntity());
        tracker.track(event.getEntity().getUniqueId(), ominous);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        TrialSpawnerTracker.SpawnContext ctx = tracker.remove(entity.getUniqueId());
        if (ctx == null) return;

        LootConfig config = configSupplier.get();
        if (!config.isEnabled()) return;

        String mobId = resolveMobId(entity);
        List<LootEntry> lootEntries = config.getLoot(mobId, ctx.isOminous());
        if (lootEntries.isEmpty()) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (LootEntry entry : lootEntries) {
            if (rng.nextDouble() < entry.chance()) {
                int amount = entry.minAmount() == entry.maxAmount()
                        ? entry.minAmount()
                        : rng.nextInt(entry.minAmount(), entry.maxAmount() + 1);
                event.getDrops().add(new ItemStack(entry.material(), amount));
            }
        }
    }

    private String resolveMobId(LivingEntity entity) {
        if (mythicHook != null) {
            String mythicId = mythicHook.getMythicMobId(entity);
            if (mythicId != null) return mythicId;
        }
        return entity.getType().name();
    }

    private boolean detectOminous(LivingEntity entity) {
        Location origin = entity.getOrigin();
        if (origin == null) return false;

        Block block = origin.getBlock();
        if (block.getType() != Material.TRIAL_SPAWNER) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block nearby = block.getRelative(dx, dy, dz);
                        if (nearby.getType() == Material.TRIAL_SPAWNER) {
                            block = nearby;
                            break;
                        }
                    }
                    if (block.getType() == Material.TRIAL_SPAWNER) break;
                }
                if (block.getType() == Material.TRIAL_SPAWNER) break;
            }
        }

        if (block.getType() == Material.TRIAL_SPAWNER) {
            try {
                org.bukkit.block.data.type.TrialSpawner spawnerData =
                        (org.bukkit.block.data.type.TrialSpawner) block.getBlockData();
                return spawnerData.isOminous();
            } catch (Exception ignored) {
            }
        }

        return false;
    }
}
