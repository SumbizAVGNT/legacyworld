package me.sumbiz.legacylock.loot;

import org.bukkit.Material;

import java.util.Map;

public record LootEntry(
        Material material,
        String mythicItemId,
        String nexoItemId,
        String base64Texture,
        Map<String, Object> itemData,
        int minAmount,
        int maxAmount,
        double chance
) {
    public LootEntry {
        if (material == null && mythicItemId == null && nexoItemId == null
                && base64Texture == null && itemData == null) {
            throw new IllegalArgumentException("At least one item source must be set");
        }
        if (minAmount < 1) throw new IllegalArgumentException("minAmount must be >= 1");
        if (maxAmount < minAmount) throw new IllegalArgumentException("maxAmount must be >= minAmount");
        if (chance < 0.0 || chance > 1.0) throw new IllegalArgumentException("chance must be 0.0-1.0");
    }

    /** Backwards-compatible constructor for simple material + mythic entries. */
    public LootEntry(Material material, String mythicItemId, int minAmount, int maxAmount, double chance) {
        this(material, mythicItemId, null, null, null, minAmount, maxAmount, chance);
    }

    public boolean isMythicItem() {
        return mythicItemId != null;
    }

    public boolean isNexoItem() {
        return nexoItemId != null;
    }

    public boolean isBase64Head() {
        return base64Texture != null;
    }

    public boolean isCustomItem() {
        return itemData != null;
    }
}
