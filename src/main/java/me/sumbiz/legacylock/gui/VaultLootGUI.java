package me.sumbiz.legacylock.gui;

import me.sumbiz.legacylock.loot.LootEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class VaultLootGUI implements Listener {

    private static final String TITLE_NORMAL = ChatColor.DARK_GREEN + "Vault Loot: Normal Key";
    private static final String TITLE_OMINOUS = ChatColor.DARK_RED + "Vault Loot: Ominous Key";
    private static final int GUI_SIZE = 54; // 6 rows
    private static final int LOOT_SLOTS = 45; // first 5 rows for items
    private static final int SAVE_SLOT = 49; // bottom row center

    private final Plugin plugin;

    // Track open GUIs: player UUID -> session data
    private final Map<UUID, GUISession> openSessions = new HashMap<>();

    public VaultLootGUI(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, boolean ominous, List<LootEntry> currentLoot) {
        String title = ominous ? TITLE_OMINOUS : TITLE_NORMAL;
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        double[] chances = new double[LOOT_SLOTS];
        int[] minAmounts = new int[LOOT_SLOTS];
        int[] maxAmounts = new int[LOOT_SLOTS];
        Arrays.fill(chances, 0.5);
        Arrays.fill(minAmounts, 1);
        Arrays.fill(maxAmounts, 1);

        // Populate with existing loot
        for (int i = 0; i < currentLoot.size() && i < LOOT_SLOTS; i++) {
            LootEntry entry = currentLoot.get(i);
            if (entry.material() != null) {
                ItemStack display = new ItemStack(entry.material(), 1);
                chances[i] = entry.chance();
                minAmounts[i] = entry.minAmount();
                maxAmounts[i] = entry.maxAmount();
                updateLore(display, chances[i], minAmounts[i], maxAmounts[i]);
                inv.setItem(i, display);
            }
        }

        // Bottom row decoration
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = LOOT_SLOTS; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Save button
        ItemStack saveBtn = createItem(Material.LIME_CONCRETE, ChatColor.GREEN + "SAVE");
        ItemMeta saveMeta = saveBtn.getItemMeta();
        saveMeta.setLore(List.of(ChatColor.GRAY + "Click to save loot table"));
        saveBtn.setItemMeta(saveMeta);
        inv.setItem(SAVE_SLOT, saveBtn);

        // Help item
        ItemStack helpBtn = createItem(Material.OAK_SIGN, ChatColor.YELLOW + "Help");
        ItemMeta helpMeta = helpBtn.getItemMeta();
        helpMeta.setLore(List.of(
                ChatColor.GRAY + "Place items in the top 5 rows",
                "",
                ChatColor.WHITE + "LMB" + ChatColor.GRAY + " = chance +1%",
                ChatColor.WHITE + "Shift+LMB" + ChatColor.GRAY + " = chance +0.1%",
                ChatColor.WHITE + "RMB" + ChatColor.GRAY + " = chance -1%",
                ChatColor.WHITE + "Shift+RMB" + ChatColor.GRAY + " = chance -0.1%",
                "",
                ChatColor.WHITE + "Middle click" + ChatColor.GRAY + " = remove item"
        ));
        helpBtn.setItemMeta(helpMeta);
        inv.setItem(45, helpBtn);

        GUISession session = new GUISession(ominous, inv, chances, minAmounts, maxAmounts);
        openSessions.put(player.getUniqueId(), session);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GUISession session = openSessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(session.inventory)) return;

        int slot = event.getRawSlot();

        // Click outside or in player inventory — allow placing items into GUI
        if (slot < 0 || slot >= GUI_SIZE) {
            // Player inventory click — allow shift-clicking items into GUI loot area
            if (event.isShiftClick() && event.getCurrentItem() != null) {
                event.setCancelled(true);
                // Find first empty loot slot
                for (int i = 0; i < LOOT_SLOTS; i++) {
                    if (session.inventory.getItem(i) == null) {
                        ItemStack clone = event.getCurrentItem().clone();
                        clone.setAmount(1);
                        session.chances[i] = 0.5;
                        session.minAmounts[i] = 1;
                        session.maxAmounts[i] = clone.getMaxStackSize();
                        updateLore(clone, session.chances[i], session.minAmounts[i], session.maxAmounts[i]);
                        session.inventory.setItem(i, clone);
                        break;
                    }
                }
            }
            return;
        }

        // Bottom row — all blocked except save
        if (slot >= LOOT_SLOTS) {
            event.setCancelled(true);
            if (slot == SAVE_SLOT) {
                save(player, session);
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Vault loot table saved!");
            }
            return;
        }

        // Loot area
        ItemStack current = session.inventory.getItem(slot);

        // Middle click = remove
        if (event.getClick() == ClickType.MIDDLE) {
            event.setCancelled(true);
            session.inventory.setItem(slot, null);
            session.chances[slot] = 0.5;
            return;
        }

        // If empty slot and player has cursor item — place it
        if (current == null) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                event.setCancelled(true);
                ItemStack placed = cursor.clone();
                placed.setAmount(1);
                session.chances[slot] = 0.5;
                session.minAmounts[slot] = 1;
                session.maxAmounts[slot] = placed.getMaxStackSize();
                updateLore(placed, session.chances[slot], session.minAmounts[slot], session.maxAmounts[slot]);
                session.inventory.setItem(slot, placed);
                player.setItemOnCursor(null);
            }
            return;
        }

        // Existing item — adjust chance
        event.setCancelled(true);
        double delta = switch (event.getClick()) {
            case LEFT -> 0.01;
            case SHIFT_LEFT -> 0.001;
            case RIGHT -> -0.01;
            case SHIFT_RIGHT -> -0.001;
            default -> 0;
        };

        if (delta != 0) {
            double newChance = Math.round((session.chances[slot] + delta) * 1000.0) / 1000.0;
            newChance = Math.max(0.001, Math.min(1.0, newChance));
            session.chances[slot] = newChance;
            updateLore(current, session.chances[slot], session.minAmounts[slot], session.maxAmounts[slot]);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GUISession session = openSessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(session.inventory)) return;

        // Block dragging into bottom row
        for (int slot : event.getRawSlots()) {
            if (slot >= LOOT_SLOTS && slot < GUI_SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openSessions.remove(event.getPlayer().getUniqueId());
    }

    private void save(Player player, GUISession session) {
        FileConfiguration config = plugin.getConfig();
        String key = session.ominous ? "vault_loot.ominous_key" : "vault_loot.normal_key";

        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < LOOT_SLOTS; i++) {
            ItemStack item = session.inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("material", item.getType().name());

            int min = session.minAmounts[i];
            int max = session.maxAmounts[i];
            if (min == max) {
                entry.put("amount", min);
            } else {
                entry.put("amount", min + "-" + max);
            }
            entry.put("chance", session.chances[i]);
            entries.add(entry);
        }

        config.set(key, entries);
        plugin.saveConfig();
    }

    private static void updateLore(ItemStack item, double chance, int minAmount, int maxAmount) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String chanceStr = String.format("%.1f%%", chance * 100);
        String amountStr = minAmount == maxAmount ? String.valueOf(minAmount) : minAmount + "-" + maxAmount;

        meta.setLore(List.of(
                "",
                ChatColor.YELLOW + "Chance: " + ChatColor.WHITE + chanceStr,
                ChatColor.YELLOW + "Amount: " + ChatColor.WHITE + amountStr,
                "",
                ChatColor.DARK_GRAY + "LMB/RMB: +/-1% | Shift: +/-0.1%"
        ));
        item.setItemMeta(meta);
    }

    private static ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class GUISession {
        final boolean ominous;
        final Inventory inventory;
        final double[] chances;
        final int[] minAmounts;
        final int[] maxAmounts;

        GUISession(boolean ominous, Inventory inventory, double[] chances, int[] minAmounts, int[] maxAmounts) {
            this.ominous = ominous;
            this.inventory = inventory;
            this.chances = chances;
            this.minAmounts = minAmounts;
            this.maxAmounts = maxAmounts;
        }
    }
}
