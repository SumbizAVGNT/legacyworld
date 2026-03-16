package me.sumbiz.legacylock.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Reflection-based hook for Nexo (formerly Oraxen) plugin.
 * Provides item ID detection and item creation without compile-time dependency.
 */
public final class NexoHook {

    private final Method idFromItemMethod;
    private final Method itemFromIdMethod;
    private final Method buildMethod;

    private NexoHook(Method idFromItemMethod, Method itemFromIdMethod, Method buildMethod) {
        this.idFromItemMethod = idFromItemMethod;
        this.itemFromIdMethod = itemFromIdMethod;
        this.buildMethod = buildMethod;
    }

    public static NexoHook create(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            return null;
        }
        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");

            // NexoItems.idFromItem(ItemStack) -> String (nullable)
            Method idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);

            // NexoItems.itemFromId(String) -> ItemBuilder (nullable)
            Method itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);

            // Determine build() method from returned ItemBuilder
            Class<?> returnType = itemFromIdMethod.getReturnType();
            Method buildMethod = returnType.getMethod("build");

            return new NexoHook(idFromItemMethod, itemFromIdMethod, buildMethod);
        } catch (Exception | NoClassDefFoundError e) {
            plugin.getLogger().warning("Nexo found but failed to hook API: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the Nexo item ID from an ItemStack.
     * Returns null if the item is not a Nexo item.
     */
    public String getItemId(ItemStack item) {
        try {
            Object result = idFromItemMethod.invoke(null, item);
            return result instanceof String s ? s : null;
        } catch (Exception | NoClassDefFoundError ignored) {
        }
        return null;
    }

    /**
     * Create an ItemStack from a Nexo item ID.
     * Returns null if the ID is not found.
     */
    public ItemStack createItem(String itemId) {
        try {
            Object builder = itemFromIdMethod.invoke(null, itemId);
            if (builder == null) return null;
            Object result = buildMethod.invoke(builder);
            if (result instanceof ItemStack item) {
                return item;
            }
        } catch (Exception | NoClassDefFoundError ignored) {
        }
        return null;
    }
}
