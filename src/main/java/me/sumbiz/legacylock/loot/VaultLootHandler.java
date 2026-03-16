package me.sumbiz.legacylock.loot;

import me.sumbiz.legacylock.hook.MythicMobsHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class VaultLootHandler implements Listener {

    private final Supplier<LootConfig> configSupplier;
    private final MythicMobsHook mythicHook;

    public VaultLootHandler(Supplier<LootConfig> configSupplier, MythicMobsHook mythicHook) {
        this.configSupplier = configSupplier;
        this.mythicHook = mythicHook;
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

        List<LootEntry> lootTable = isOminous ? config.getOminousKeyLoot() : config.getNormalKeyLoot();
        if (lootTable.isEmpty()) return;

        if (config.isReplaceVanillaLoot()) {
            event.setCancelled(true);

            // Consume one key
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                event.getPlayer().getInventory().setItemInMainHand(null);
            }
        }

        dropLoot(event.getPlayer(), block.getLocation().add(0.5, 1.0, 0.5), lootTable);
    }

    private void dropLoot(Player player, Location dropLocation, List<LootEntry> lootTable) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (LootEntry entry : lootTable) {
            if (rng.nextDouble() >= entry.chance()) continue;

            int amount = entry.minAmount() == entry.maxAmount()
                    ? entry.minAmount()
                    : rng.nextInt(entry.minAmount(), entry.maxAmount() + 1);

            ItemStack item;
            if (entry.isMythicItem() && mythicHook != null) {
                item = mythicHook.getMythicItem(entry.mythicItemId());
                if (item == null) continue;
                item.setAmount(amount);
            } else if (entry.material() != null) {
                item = new ItemStack(entry.material(), amount);
            } else {
                continue;
            }

            dropLocation.getWorld().dropItemNaturally(dropLocation, item);
        }
    }
}
