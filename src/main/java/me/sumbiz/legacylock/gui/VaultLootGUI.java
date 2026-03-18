package me.sumbiz.legacylock.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.sumbiz.legacylock.hook.MythicMobsHook;
import me.sumbiz.legacylock.hook.NexoHook;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class VaultLootGUI implements Listener {

    private static final String TITLE_NORMAL = ChatColor.DARK_GREEN + "Vault Loot: Normal Key";
    private static final String TITLE_OMINOUS = ChatColor.DARK_RED + "Vault Loot: Ominous Key";
    private static final int GUI_SIZE = 54; // 6 rows
    private static final int LOOT_SLOTS = 45; // first 5 rows for items
    private static final int SAVE_SLOT = 49; // bottom row center

    private final Plugin plugin;
    private final Runnable onSaveCallback;
    private MythicMobsHook mythicHook;
    private NexoHook nexoHook;

    // Track open GUIs: player UUID -> session data
    private final Map<UUID, GUISession> openSessions = new HashMap<>();

    public VaultLootGUI(Plugin plugin, Runnable onSaveCallback) {
        this.plugin = plugin;
        this.onSaveCallback = onSaveCallback;
    }

    public void setMythicHook(MythicMobsHook mythicHook) {
        this.mythicHook = mythicHook;
    }

    public void setNexoHook(NexoHook nexoHook) {
        this.nexoHook = nexoHook;
    }

    public void open(Player player, boolean ominous, List<LootEntry> currentLoot) {
        String title = ominous ? TITLE_OMINOUS : TITLE_NORMAL;
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        double[] chances = new double[LOOT_SLOTS];
        int[] minAmounts = new int[LOOT_SLOTS];
        int[] maxAmounts = new int[LOOT_SLOTS];
        ItemStack[] cleanItems = new ItemStack[LOOT_SLOTS];
        String[] nexoIds = new String[LOOT_SLOTS];
        String[] mythicIds = new String[LOOT_SLOTS];

        Arrays.fill(chances, 0.5);
        Arrays.fill(minAmounts, 1);
        Arrays.fill(maxAmounts, 1);

        // Populate with existing loot
        for (int i = 0; i < currentLoot.size() && i < LOOT_SLOTS; i++) {
            LootEntry entry = currentLoot.get(i);
            ItemStack display = resolveEntryToItem(entry);
            if (display == null) continue;

            chances[i] = entry.chance();
            minAmounts[i] = entry.minAmount();
            maxAmounts[i] = entry.maxAmount();
            cleanItems[i] = display.clone();

            if (entry.isNexoItem()) nexoIds[i] = entry.nexoItemId();
            if (entry.isMythicItem()) mythicIds[i] = entry.mythicItemId();

            updateLore(display, chances[i], minAmounts[i], maxAmounts[i], nexoIds[i], mythicIds[i]);
            inv.setItem(i, display);
        }

        // Bottom row decoration
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = LOOT_SLOTS; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Save button
        ItemStack saveBtn = createItem(Material.LIME_CONCRETE, ChatColor.GREEN + "SAVE");
        ItemMeta saveMeta = saveBtn.getItemMeta();
        saveMeta.setLore(List.of(ChatColor.GRAY + "Нажмите, чтобы сохранить таблицу лута"));
        saveBtn.setItemMeta(saveMeta);
        inv.setItem(SAVE_SLOT, saveBtn);

        // Help item
        ItemStack helpBtn = createItem(Material.OAK_SIGN, ChatColor.YELLOW + "Помощь");
        ItemMeta helpMeta = helpBtn.getItemMeta();
        helpMeta.setLore(List.of(
                ChatColor.GRAY + "Поместите предметы в верхние 5 рядов",
                "",
                ChatColor.WHITE + "ЛКМ" + ChatColor.GRAY + " = шанс +1%",
                ChatColor.WHITE + "Shift+ЛКМ" + ChatColor.GRAY + " = шанс +0.1%",
                ChatColor.WHITE + "ПКМ" + ChatColor.GRAY + " = шанс -1%",
                ChatColor.WHITE + "Shift+ПКМ" + ChatColor.GRAY + " = шанс -0.1%",
                "",
                ChatColor.WHITE + "Q" + ChatColor.GRAY + " = макс. кол-во +1",
                ChatColor.WHITE + "Ctrl+Q" + ChatColor.GRAY + " = макс. кол-во -1",
                ChatColor.WHITE + "1" + ChatColor.GRAY + " = мин. кол-во +1",
                ChatColor.WHITE + "2" + ChatColor.GRAY + " = мин. кол-во -1",
                "",
                ChatColor.WHITE + "Средняя кнопка" + ChatColor.GRAY + " = удалить предмет"
        ));
        helpBtn.setItemMeta(helpMeta);
        inv.setItem(45, helpBtn);

        GUISession session = new GUISession(ominous, inv, chances, minAmounts, maxAmounts,
                cleanItems, nexoIds, mythicIds);
        openSessions.put(player.getUniqueId(), session);

        player.openInventory(inv);
    }

    /**
     * Resolve a LootEntry into a display ItemStack.
     */
    private ItemStack resolveEntryToItem(LootEntry entry) {
        if (entry.isNexoItem() && nexoHook != null) {
            ItemStack item = nexoHook.createItem(entry.nexoItemId());
            if (item != null) return item;
        }
        if (entry.isMythicItem() && mythicHook != null) {
            ItemStack item = mythicHook.getMythicItem(entry.mythicItemId());
            if (item != null) return item;
        }
        if (entry.isBase64Head()) {
            return createBase64Head(entry.base64Texture());
        }
        if (entry.isCustomItem()) {
            try {
                return ItemStack.deserialize(entry.itemData());
            } catch (Exception e) {
                plugin.getLogger().warning("[VaultLootGUI] Failed to deserialize item_data: " + e.getMessage());
            }
        }
        if (entry.material() != null) {
            return new ItemStack(entry.material(), 1);
        }
        return null;
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
                        placeItem(session, i, event.getCurrentItem());
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
                player.sendMessage(ChatColor.GREEN + "Таблица лута сохранена и применена!");
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
            session.minAmounts[slot] = 1;
            session.maxAmounts[slot] = 1;
            session.cleanItems[slot] = null;
            session.nexoIds[slot] = null;
            session.mythicIds[slot] = null;
            return;
        }

        // If empty slot and player has cursor item — place it
        if (current == null) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                event.setCancelled(true);
                placeItem(session, slot, cursor);
                player.setItemOnCursor(null);
            }
            return;
        }

        // Existing item — adjust values
        event.setCancelled(true);
        ClickType click = event.getClick();

        // Amount editing: DROP (Q), CONTROL_DROP (Ctrl+Q), NUMBER_KEY 1/2
        if (click == ClickType.DROP) {
            // Max amount +1 (capped at 64)
            session.maxAmounts[slot] = Math.min(64, session.maxAmounts[slot] + 1);
            updateLore(current, session.chances[slot], session.minAmounts[slot], session.maxAmounts[slot],
                    session.nexoIds[slot], session.mythicIds[slot]);
            return;
        }
        if (click == ClickType.CONTROL_DROP) {
            // Max amount -1 (min = minAmount, at least 1)
            session.maxAmounts[slot] = Math.max(session.minAmounts[slot], session.maxAmounts[slot] - 1);
            updateLore(current, session.chances[slot], session.minAmounts[slot], session.maxAmounts[slot],
                    session.nexoIds[slot], session.mythicIds[slot]);
            return;
        }
        if (click == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar == 0) {
                // Key 1: min amount +1 (capped at maxAmount)
                session.minAmounts[slot] = Math.min(session.maxAmounts[slot], session.minAmounts[slot] + 1);
                updateLore(current, session.chances[slot], session.minAmounts[slot], session.maxAmounts[slot],
                        session.nexoIds[slot], session.mythicIds[slot]);
            } else if (hotbar == 1) {
                // Key 2: min amount -1 (min 1)
                session.minAmounts[slot] = Math.max(1, session.minAmounts[slot] - 1);
                updateLore(current, session.chances[slot], session.minAmounts[slot], session.maxAmounts[slot],
                        session.nexoIds[slot], session.mythicIds[slot]);
            }
            return;
        }

        // Chance editing
        double delta = switch (click) {
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
            updateLore(current, session.chances[slot], session.minAmounts[slot], session.maxAmounts[slot],
                    session.nexoIds[slot], session.mythicIds[slot]);
        }
    }

    /**
     * Place an item from the player into a GUI slot, detecting Nexo items and storing clean copy.
     */
    private void placeItem(GUISession session, int slot, ItemStack source) {
        ItemStack clean = source.clone();
        clean.setAmount(1);

        // Detect Nexo item
        String nexoId = null;
        if (nexoHook != null) {
            nexoId = nexoHook.getItemId(clean);
        }

        session.cleanItems[slot] = clean;
        session.nexoIds[slot] = nexoId;
        session.mythicIds[slot] = null; // cannot detect mythic items from ItemStack
        session.chances[slot] = 0.5;
        session.minAmounts[slot] = 1;
        session.maxAmounts[slot] = 1;

        ItemStack display = clean.clone();
        updateLore(display, session.chances[slot], session.minAmounts[slot], session.maxAmounts[slot],
                nexoId, null);
        session.inventory.setItem(slot, display);
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

            int min = session.minAmounts[i];
            int max = session.maxAmounts[i];
            double chance = session.chances[i];

            // Determine item type for serialization
            if (session.nexoIds[i] != null) {
                entry.put("nexo_item", session.nexoIds[i]);
            } else if (session.mythicIds[i] != null) {
                entry.put("mythic_item", session.mythicIds[i]);
            } else {
                ItemStack clean = session.cleanItems[i];
                if (clean == null) clean = item;

                // Check for base64 player head
                String base64 = extractBase64Texture(clean);
                if (base64 != null) {
                    entry.put("base64_head", base64);
                } else if (hasNonTrivialMeta(clean)) {
                    // Full item serialization for items with custom NBT
                    entry.put("item_data", clean.serialize());
                } else {
                    entry.put("material", clean.getType().name());
                }
            }

            if (min == max) {
                entry.put("amount", min);
            } else {
                entry.put("amount", min + "-" + max);
            }
            entry.put("chance", chance);
            entries.add(entry);
        }

        config.set(key, entries);
        plugin.saveConfig();

        // Auto-reload loot config so changes apply immediately
        if (onSaveCallback != null) {
            onSaveCallback.run();
        }
    }

    /**
     * Extract base64 texture from a player head, or null if not a custom head.
     */
    private static String extractBase64Texture(ItemStack item) {
        if (item.getType() != Material.PLAYER_HEAD) return null;
        if (!(item.getItemMeta() instanceof SkullMeta skullMeta)) return null;

        PlayerProfile profile = skullMeta.getPlayerProfile();
        if (profile == null) return null;

        for (ProfileProperty prop : profile.getProperties()) {
            if ("textures".equals(prop.getName())) {
                return prop.getValue();
            }
        }
        return null;
    }

    /**
     * Check if an item has non-trivial metadata (enchantments, custom name, etc.)
     * that requires full serialization instead of just saving the material name.
     */
    private static boolean hasNonTrivialMeta(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName()
                || meta.hasLore()
                || meta.hasEnchants()
                || meta.hasCustomModelData()
                || !meta.getItemFlags().isEmpty()
                || meta.isUnbreakable()
                || meta.hasAttributeModifiers();
    }

    /**
     * Create a player head with a base64 texture.
     */
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

    private static void updateLore(ItemStack item, double chance, int minAmount, int maxAmount,
                                   String nexoId, String mythicId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String chanceStr = String.format("%.1f%%", chance * 100);
        String amountStr = minAmount == maxAmount ? String.valueOf(minAmount) : minAmount + "-" + maxAmount;

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (nexoId != null) {
            lore.add(ChatColor.LIGHT_PURPLE + "Nexo: " + ChatColor.WHITE + nexoId);
        }
        if (mythicId != null) {
            lore.add(ChatColor.LIGHT_PURPLE + "MythicMobs: " + ChatColor.WHITE + mythicId);
        }
        lore.add(ChatColor.YELLOW + "Шанс: " + ChatColor.WHITE + chanceStr);
        lore.add(ChatColor.YELLOW + "Кол-во: " + ChatColor.WHITE + amountStr);
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "ЛКМ/ПКМ: шанс | Q: макс | 1/2: мин");

        meta.setLore(lore);
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
        final ItemStack[] cleanItems;
        final String[] nexoIds;
        final String[] mythicIds;

        GUISession(boolean ominous, Inventory inventory, double[] chances, int[] minAmounts, int[] maxAmounts,
                   ItemStack[] cleanItems, String[] nexoIds, String[] mythicIds) {
            this.ominous = ominous;
            this.inventory = inventory;
            this.chances = chances;
            this.minAmounts = minAmounts;
            this.maxAmounts = maxAmounts;
            this.cleanItems = cleanItems;
            this.nexoIds = nexoIds;
            this.mythicIds = mythicIds;
        }
    }
}
