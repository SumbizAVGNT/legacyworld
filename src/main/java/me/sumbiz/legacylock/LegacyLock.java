package me.sumbiz.legacylock;

import me.sumbiz.legacylock.gui.VaultLootGUI;
import me.sumbiz.legacylock.hook.MythicMobsHook;
import me.sumbiz.legacylock.hook.NexoHook;
import me.sumbiz.legacylock.loot.LootConfig;
import me.sumbiz.legacylock.loot.TrialSpawnerMobHandler;
import me.sumbiz.legacylock.loot.VaultLootHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public class LegacyLock extends JavaPlugin implements Listener {

    private static final int DATAPACK_FORMAT_1_21_4 = 61;
    private static final int TRIAL_CHAMBERS_SALT = 94251327;

    private final Set<Material> banned = EnumSet.noneOf(Material.class);

    // Cached config flags
    private volatile boolean blockLootAndDrops = true;
    private volatile boolean blockPickup = true;
    private volatile boolean blockCrafting = true;
    private volatile boolean blockPlace = true;
    private volatile boolean blockGiveCommand = true;
    private volatile boolean cleanOnJoin = true;

    // Vault loot & mob replacement
    private volatile LootConfig lootConfig;
    private VaultLootGUI vaultLootGUI;

    @Override
    public void onEnable() {
        getLogger().info("LegacyLock enabling...");

        saveDefaultConfig();
        reloadAllConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("enable_datapack_disable_trial_chambers", true)) {
            for (World w : Bukkit.getWorlds()) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> ensureDatapackExists(w));
            }
        }

        if (cleanOnJoin) {
            for (Player p : Bukkit.getOnlinePlayers()) scrubPlayer(p);
        }

        int period = Math.max(5, getConfig().getInt("clean_period_seconds", 30));
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) scrubPlayer(p);
        }, 20L * period, 20L * period);

        // GUI — pass auto-reload callback
        this.vaultLootGUI = new VaultLootGUI(this, this::reloadLootConfig);
        Bukkit.getPluginManager().registerEvents(vaultLootGUI, this);

        // Vault loot & hooks
        initLootSystem();

        getLogger().info("LegacyLock enabled. Banned materials: " + banned.size());
    }

    private void initLootSystem() {
        MythicMobsHook mythicHook = MythicMobsHook.create(this);
        if (mythicHook != null) {
            getLogger().info("MythicMobs integration enabled.");
        }

        NexoHook nexoHook = NexoHook.create(this);
        if (nexoHook != null) {
            getLogger().info("Nexo integration enabled.");
        }

        // Pass hooks to GUI for item detection/display
        vaultLootGUI.setMythicHook(mythicHook);
        vaultLootGUI.setNexoHook(nexoHook);

        if (lootConfig.isEnabled()) {
            VaultLootHandler vaultHandler = new VaultLootHandler(this, () -> lootConfig, mythicHook, nexoHook);
            Bukkit.getPluginManager().registerEvents(vaultHandler, this);
            getLogger().info("Vault loot system enabled.");
        }

        if (lootConfig.isMobReplacementEnabled() && mythicHook != null) {
            TrialSpawnerMobHandler mobHandler = new TrialSpawnerMobHandler(mythicHook, () -> lootConfig);
            Bukkit.getPluginManager().registerEvents(mobHandler, this);
            getLogger().info("Trial spawner mob replacement enabled.");
        } else if (lootConfig.isMobReplacementEnabled() && mythicHook == null) {
            getLogger().warning("trial_spawner_mobs.enabled=true but MythicMobs not found! Mob replacement disabled.");
        }
    }

    /**
     * Reload only the loot config (called after GUI save for instant apply).
     */
    private void reloadLootConfig() {
        reloadConfig();
        this.lootConfig = new LootConfig(getConfig(), getLogger());
    }

    private void reloadAllConfig() {
        reloadBans();
        cacheConfigFlags();
        this.lootConfig = new LootConfig(getConfig(), getLogger());
    }

    private void reloadBans() {
        banned.clear();
        for (String s : getConfig().getStringList("banned_materials")) {
            String key = s.trim().toUpperCase(Locale.ROOT);
            try {
                banned.add(Material.valueOf(key));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Unknown Material (ignored): " + s);
            }
        }
    }

    private void cacheConfigFlags() {
        blockLootAndDrops = getConfig().getBoolean("block_loot_and_drops", true);
        blockPickup = getConfig().getBoolean("block_pickup", true);
        blockCrafting = getConfig().getBoolean("block_crafting", true);
        blockPlace = getConfig().getBoolean("block_place", true);
        blockGiveCommand = getConfig().getBoolean("block_give_command", true);
        cleanOnJoin = getConfig().getBoolean("clean_on_join", true);
    }

    private boolean isBanned(Material m) {
        return banned.contains(m);
    }

    // --- Trial Chambers OFF via datapack ---
    @EventHandler
    public void onWorldInit(WorldInitEvent e) {
        if (!getConfig().getBoolean("enable_datapack_disable_trial_chambers", true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> ensureDatapackExists(e.getWorld()));
    }

    private void ensureDatapackExists(World world) {
        String folderName = getConfig().getString("datapack_folder_name", "legacy_disable_trials");
        Path root = world.getWorldFolder().toPath().resolve("datapacks").resolve(folderName);

        try {
            Files.createDirectories(root);

            String mcmeta = """
                    {
                      "pack": {
                        "pack_format": %d,
                        "description": "LegacyLock: disables Trial Chambers worldgen"
                      }
                    }
                    """.formatted(DATAPACK_FORMAT_1_21_4);

            Files.writeString(
                    root.resolve("pack.mcmeta"),
                    mcmeta,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            Path structureSet = root.resolve("data/minecraft/worldgen/structure_set");
            Files.createDirectories(structureSet);

            String trialChambersOverride = """
                    {
                      "placement": {
                        "type": "minecraft:random_spread",
                        "salt": %d,
                        "separation": 4095,
                        "spacing": 4096
                      },
                      "structures": [
                        { "structure": "minecraft:trial_chambers", "weight": 1 }
                      ]
                    }
                    """.formatted(TRIAL_CHAMBERS_SALT);

            Files.writeString(
                    structureSet.resolve("trial_chambers.json"),
                    trialChambersOverride,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } catch (IOException ex) {
            getLogger().severe("Failed to write datapack for world " + world.getName() + ": " + ex.getMessage());
        }
    }

    // --- Loot / drops ---
    @EventHandler
    public void onLootGenerate(LootGenerateEvent e) {
        if (!blockLootAndDrops) return;
        e.getLoot().removeIf(it -> it != null && isBanned(it.getType()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent e) {
        if (!blockLootAndDrops) return;
        e.getDrops().removeIf(it -> it != null && isBanned(it.getType()));
    }

    // --- Pickup ---
    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!blockPickup) return;
        ItemStack st = e.getItem().getItemStack();
        if (st != null && isBanned(st.getType())) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    // --- Crafting / smithing / anvil ---
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (!blockCrafting) return;
        ItemStack result = e.getInventory().getResult();
        if (result != null && isBanned(result.getType())) {
            e.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent e) {
        if (!blockCrafting) return;
        ItemStack result = e.getResult();
        if (result != null && isBanned(result.getType())) {
            e.setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        if (!blockCrafting) return;
        ItemStack result = e.getResult();
        if (result != null && isBanned(result.getType())) {
            e.setResult(new ItemStack(Material.AIR));
        }
    }

    // --- Place blocks ---
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!blockPlace) return;
        if (isBanned(e.getBlockPlaced().getType())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cЗапрещено (выше 1.20.6): §e" + e.getBlockPlaced().getType());
        }
    }

    // --- Block /give ---
    @EventHandler
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (!blockGiveCommand) return;

        String msg = e.getMessage().toLowerCase(Locale.ROOT);
        if (!(msg.startsWith("/give ") || msg.startsWith("/minecraft:give "))) return;

        String[] parts = msg.split("\\s+");
        if (parts.length < 3) return;

        String item = parts[2].toUpperCase(Locale.ROOT);
        if (item.contains(":")) item = item.substring(item.indexOf(':') + 1);
        item = item.replaceAll("[^A-Z0-9_]", "");

        try {
            Material m = Material.valueOf(item);
            if (isBanned(m)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§c/give запрещён для предметов выше 1.20.6: §e" + m);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    // --- Inventory cleaning ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!cleanOnJoin) return;
        scrubPlayer(e.getPlayer());
    }

    private void scrubPlayer(Player p) {
        scrubInventory(p.getInventory());
        scrubInventory(p.getEnderChest());
    }

    private void scrubInventory(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && isBanned(it.getType())) {
                inv.setItem(i, null);
            }
        }
    }

    // --- Commands ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("legacylock")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            reloadAllConfig();
            sender.sendMessage("§aLegacyLock config reloaded. Banned: " + banned.size()
                    + " | Vault loot: " + (lootConfig.isEnabled() ? "ON" : "OFF")
                    + " | Mob replace: " + (lootConfig.isMobReplacementEnabled() ? "ON" : "OFF"));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }

            boolean ominous;
            if (args[1].equalsIgnoreCase("normal")) {
                ominous = false;
            } else if (args[1].equalsIgnoreCase("ominous")) {
                ominous = true;
            } else {
                sender.sendMessage("§cUsage: /legacylock edit <normal|ominous>");
                return true;
            }

            var lootList = ominous ? lootConfig.getOminousKeyLoot() : lootConfig.getNormalKeyLoot();
            vaultLootGUI.open(player, ominous, lootList);
            return true;
        }

        sender.sendMessage("§7/legacylock reload");
        sender.sendMessage("§7/legacylock edit <normal|ominous>");
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("legacylock")) return java.util.List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return java.util.List.of("reload", "edit").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return java.util.List.of("normal", "ominous").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return java.util.List.of();
    }
}
