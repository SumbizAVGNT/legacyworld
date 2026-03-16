package me.sumbiz.legacylock.loot;

import org.bukkit.Material;

public record LootEntry(Material material, int minAmount, int maxAmount, double chance) {

    public LootEntry {
        if (minAmount < 1) throw new IllegalArgumentException("minAmount must be >= 1");
        if (maxAmount < minAmount) throw new IllegalArgumentException("maxAmount must be >= minAmount");
        if (chance < 0.0 || chance > 1.0) throw new IllegalArgumentException("chance must be 0.0-1.0");
    }
}
