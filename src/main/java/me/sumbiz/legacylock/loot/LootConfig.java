package me.sumbiz.legacylock.loot;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.logging.Logger;

public final class LootConfig {

    private final boolean enabled;
    private final boolean replaceVanillaLoot;
    private final int vaultCooldownSeconds;
    private final int animationDelayTicks;
    private final List<LootEntry> normalKeyLoot;
    private final List<LootEntry> ominousKeyLoot;

    private final boolean mobReplacementEnabled;
    private final Map<EntityType, String> mobReplacements;

    public LootConfig(FileConfiguration config, Logger logger) {
        ConfigurationSection vaultSection = config.getConfigurationSection("vault_loot");
        if (vaultSection == null) {
            this.enabled = false;
            this.replaceVanillaLoot = true;
            this.vaultCooldownSeconds = 0;
            this.animationDelayTicks = 0;
            this.normalKeyLoot = List.of();
            this.ominousKeyLoot = List.of();
        } else {
            this.enabled = vaultSection.getBoolean("enabled", true);
            this.replaceVanillaLoot = vaultSection.getBoolean("replace_vanilla_loot", true);
            this.vaultCooldownSeconds = Math.max(0, vaultSection.getInt("cooldown_seconds", 5));
            this.animationDelayTicks = Math.max(0, vaultSection.getInt("animation_delay_ticks", 30));
            this.normalKeyLoot = Collections.unmodifiableList(
                    parseLootEntries(vaultSection.getMapList("normal_key"), logger, "vault_loot.normal_key"));
            this.ominousKeyLoot = Collections.unmodifiableList(
                    parseLootEntries(vaultSection.getMapList("ominous_key"), logger, "vault_loot.ominous_key"));
        }

        ConfigurationSection mobSection = config.getConfigurationSection("trial_spawner_mobs");
        if (mobSection == null || !mobSection.getBoolean("enabled", false)) {
            this.mobReplacementEnabled = false;
            this.mobReplacements = Map.of();
        } else {
            this.mobReplacementEnabled = true;
            ConfigurationSection replacements = mobSection.getConfigurationSection("replacements");
            if (replacements == null) {
                this.mobReplacements = Map.of();
            } else {
                Map<EntityType, String> map = new EnumMap<>(EntityType.class);
                for (String key : replacements.getKeys(false)) {
                    try {
                        EntityType type = EntityType.valueOf(key.trim().toUpperCase(Locale.ROOT));
                        String mythicMobId = replacements.getString(key);
                        if (mythicMobId != null && !mythicMobId.isBlank()) {
                            map.put(type, mythicMobId.trim());
                        }
                    } catch (IllegalArgumentException ex) {
                        logger.warning("[LootConfig] Unknown EntityType in trial_spawner_mobs.replacements: " + key);
                    }
                }
                this.mobReplacements = Collections.unmodifiableMap(map);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isReplaceVanillaLoot() {
        return replaceVanillaLoot;
    }

    public int getVaultCooldownSeconds() {
        return vaultCooldownSeconds;
    }

    public int getAnimationDelayTicks() {
        return animationDelayTicks;
    }

    public List<LootEntry> getNormalKeyLoot() {
        return normalKeyLoot;
    }

    public List<LootEntry> getOminousKeyLoot() {
        return ominousKeyLoot;
    }

    public boolean isMobReplacementEnabled() {
        return mobReplacementEnabled;
    }

    public Map<EntityType, String> getMobReplacements() {
        return mobReplacements;
    }

    public String getMobReplacement(EntityType type) {
        return mobReplacements.get(type);
    }

    private static List<LootEntry> parseLootEntries(List<Map<?, ?>> list, Logger logger, String context) {
        if (list == null || list.isEmpty()) return List.of();

        List<LootEntry> result = new ArrayList<>(list.size());
        for (Map<?, ?> entry : list) {
            try {
                String mythicItemId = null;
                Material mat = null;

                Object mythicObj = entry.get("mythic_item");
                if (mythicObj != null) {
                    mythicItemId = String.valueOf(mythicObj).trim();
                } else {
                    String matName = String.valueOf(entry.get("material")).trim().toUpperCase(Locale.ROOT);
                    mat = Material.valueOf(matName);
                }

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

                result.add(new LootEntry(mat, mythicItemId, minAmount, maxAmount, chance));
            } catch (Exception ex) {
                logger.warning("[LootConfig] Skipping invalid loot entry in '" + context + "': " + ex.getMessage());
            }
        }
        return result;
    }
}
