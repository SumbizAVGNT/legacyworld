package me.sumbiz.legacylock.loot;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Logger;

public final class LootConfig {

    private final boolean enabled;
    private final long maxTrackDurationMillis;
    private final int cleanupIntervalSeconds;

    private final Map<String, List<LootEntry>> normalKeyLoot;
    private final Map<String, List<LootEntry>> ominousKeyLoot;
    private final List<LootEntry> normalKeyDefault;
    private final List<LootEntry> ominousKeyDefault;

    public LootConfig(FileConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("trial_spawner_loot");
        if (section == null) {
            this.enabled = false;
            this.maxTrackDurationMillis = 30L * 60_000;
            this.cleanupIntervalSeconds = 300;
            this.normalKeyLoot = Map.of();
            this.ominousKeyLoot = Map.of();
            this.normalKeyDefault = List.of();
            this.ominousKeyDefault = List.of();
            return;
        }

        this.enabled = section.getBoolean("enabled", true);
        this.maxTrackDurationMillis = section.getInt("max_track_duration_minutes", 30) * 60_000L;
        this.cleanupIntervalSeconds = Math.max(10, section.getInt("cleanup_interval_seconds", 300));

        this.normalKeyDefault = parseLootList(section, "normal.default", logger);
        this.ominousKeyDefault = parseLootList(section, "ominous.default", logger);

        this.normalKeyLoot = Collections.unmodifiableMap(parseMobLootMap(section.getConfigurationSection("normal"), logger));
        this.ominousKeyLoot = Collections.unmodifiableMap(parseMobLootMap(section.getConfigurationSection("ominous"), logger));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getMaxTrackDurationMillis() {
        return maxTrackDurationMillis;
    }

    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public List<LootEntry> getLoot(String mobId, boolean isOminous) {
        Map<String, List<LootEntry>> map = isOminous ? ominousKeyLoot : normalKeyLoot;
        List<LootEntry> specific = map.get(mobId);
        if (specific != null) return specific;
        return isOminous ? ominousKeyDefault : normalKeyDefault;
    }

    private static Map<String, List<LootEntry>> parseMobLootMap(ConfigurationSection section, Logger logger) {
        if (section == null) return Map.of();

        Map<String, List<LootEntry>> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            if (key.equals("default")) continue;
            List<LootEntry> entries = parseLootEntries(section.getMapList(key), logger, key);
            if (!entries.isEmpty()) {
                map.put(key, Collections.unmodifiableList(entries));
            }
        }
        return map;
    }

    private static List<LootEntry> parseLootList(ConfigurationSection parent, String path, Logger logger) {
        if (parent == null) return List.of();
        List<?> raw = parent.getList(path);
        if (raw == null) return List.of();
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> mapList = (List<Map<?, ?>>) raw;
        return Collections.unmodifiableList(parseLootEntries(mapList, logger, path));
    }

    private static List<LootEntry> parseLootEntries(List<Map<?, ?>> list, Logger logger, String context) {
        if (list == null || list.isEmpty()) return List.of();

        List<LootEntry> result = new ArrayList<>(list.size());
        for (Map<?, ?> entry : list) {
            try {
                String matName = String.valueOf(entry.get("material")).trim().toUpperCase(Locale.ROOT);
                Material mat = Material.valueOf(matName);

                double chance = ((Number) entry.get("chance")).doubleValue();

                Object amountObj = entry.get("amount");
                int minAmount, maxAmount;
                if (amountObj instanceof String s && s.contains("-")) {
                    String[] parts = s.split("-", 2);
                    minAmount = Integer.parseInt(parts[0].trim());
                    maxAmount = Integer.parseInt(parts[1].trim());
                } else {
                    int amt = amountObj instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(amountObj).trim());
                    minAmount = amt;
                    maxAmount = amt;
                }

                result.add(new LootEntry(mat, minAmount, maxAmount, chance));
            } catch (Exception ex) {
                logger.warning("[LootConfig] Skipping invalid loot entry in '" + context + "': " + ex.getMessage());
            }
        }
        return result;
    }
}
