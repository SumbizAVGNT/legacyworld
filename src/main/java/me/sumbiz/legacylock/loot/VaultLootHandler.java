package me.sumbiz.legacylock.loot;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.sumbiz.legacylock.hook.MythicMobsHook;
import me.sumbiz.legacylock.hook.NexoHook;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class VaultLootHandler implements Listener {

    private final Plugin plugin;
    private final Supplier<LootConfig> configSupplier;
    private final MythicMobsHook mythicHook;
    private final NexoHook nexoHook;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public VaultLootHandler(Plugin plugin, Supplier<LootConfig> configSupplier,
                            MythicMobsHook mythicHook, NexoHook nexoHook) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.mythicHook = mythicHook;
        this.nexoHook = nexoHook;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.VAULT) return;

        ItemStack hand = event.getItem();
        if (hand == null) return;

        Material handType = hand.getType();
        boolean isOminous;
        if (handType == Material.TRIAL_KEY) {
            isOminous = false;
        } else if (handType == Material.OMINOUS_TRIAL_KEY) {
            isOminous = true;
        } else {
            return;
        }

        LootConfig config = configSupplier.get();
        if (!config.isEnabled()) return;

        Player player = event.getPlayer();

        // Cooldown check
        long cooldownMs = config.getVaultCooldownSeconds() * 1000L;
        if (cooldownMs > 0) {
            Long lastUse = cooldowns.get(player.getUniqueId());
            if (lastUse != null) {
                long remaining = (lastUse + cooldownMs) - System.currentTimeMillis();
                if (remaining > 0) {
                    event.setCancelled(true);
                    player.sendMessage("§cПодождите §e" + (remaining / 1000 + 1) + " сек§c перед повторным использованием.");
                    return;
                }
            }
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }

        List<LootEntry> lootTable = isOminous ? config.getOminousKeyLoot() : config.getNormalKeyLoot();
        if (lootTable.isEmpty()) return;

        if (config.isReplaceVanillaLoot()) {
            event.setCancelled(true);

            // Consume one key
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        Location vaultLoc = block.getLocation().add(0.5, 0.5, 0.5);
        Location dropLoc = block.getLocation().add(0.5, 1.0, 0.5);
        int animationTicks = config.getAnimationDelayTicks();

        if (animationTicks > 0) {
            playAnimation(player, vaultLoc, animationTicks, () -> dropLoot(dropLoc, lootTable));
        } else {
            dropLoot(dropLoc, lootTable);
        }
    }

    private void playAnimation(Player player, Location loc, int totalTicks, Runnable onComplete) {
        World world = loc.getWorld();

        // Initial sound
        world.playSound(loc, Sound.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Particle ticks: spiral particles every 4 ticks
        int steps = totalTicks / 4;
        for (int i = 0; i < steps; i++) {
            final int step = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double angle = (step * 2.0 * Math.PI) / steps;
                double radius = 0.6;
                double x = loc.getX() + Math.cos(angle) * radius;
                double z = loc.getZ() + Math.sin(angle) * radius;
                double y = loc.getY() + (step * 0.8 / steps);

                world.spawnParticle(Particle.ENCHANT, x, y, z, 3, 0.1, 0.1, 0.1, 0.05);
                world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0.02);

                if (step == steps / 2) {
                    world.playSound(loc, Sound.BLOCK_VAULT_ACTIVATE, SoundCategory.BLOCKS, 0.8f, 1.2f);
                }
            }, (long) i * 4);
        }

        // Final burst + loot drop
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.getX(), loc.getY() + 0.8, loc.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
            world.playSound(loc, Sound.BLOCK_VAULT_CLOSE_SHUTTER, SoundCategory.BLOCKS, 1.0f, 1.4f);
            onComplete.run();
        }, totalTicks);
    }

    private void dropLoot(Location dropLocation, List<LootEntry> lootTable) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (LootEntry entry : lootTable) {
            if (rng.nextDouble() >= entry.chance()) continue;

            int amount = entry.minAmount() == entry.maxAmount()
                    ? entry.minAmount()
                    : rng.nextInt(entry.minAmount(), entry.maxAmount() + 1);

            ItemStack item = resolveItem(entry);
            if (item == null) continue;

            item.setAmount(amount);
            dropLocation.getWorld().dropItemNaturally(dropLocation, item);
        }
    }

    /**
     * Resolve a LootEntry into an ItemStack for dropping.
     */
    private ItemStack resolveItem(LootEntry entry) {
        // Nexo item
        if (entry.isNexoItem() && nexoHook != null) {
            ItemStack item = nexoHook.createItem(entry.nexoItemId());
            if (item != null) return item;
        }

        // MythicMobs item
        if (entry.isMythicItem() && mythicHook != null) {
            ItemStack item = mythicHook.getMythicItem(entry.mythicItemId());
            if (item != null) return item;
        }

        // Base64 player head
        if (entry.isBase64Head()) {
            return createBase64Head(entry.base64Texture());
        }

        // Custom item (full NBT serialization)
        if (entry.isCustomItem()) {
            try {
                return ItemStack.deserialize(entry.itemData());
            } catch (Exception e) {
                return null;
            }
        }

        // Simple vanilla material
        if (entry.material() != null) {
            return new ItemStack(entry.material());
        }

        return null;
    }

    private static ItemStack createBase64Head(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "");
            profile.getProperties().add(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        }
        return head;
    }

    public void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
    }

    public void clearAllCooldowns() {
        cooldowns.clear();
    }
}
