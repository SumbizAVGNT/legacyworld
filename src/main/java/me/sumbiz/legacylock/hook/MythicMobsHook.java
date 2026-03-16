package me.sumbiz.legacylock.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class MythicMobsHook {

    // MobManager methods
    private final Method instMethod;
    private final Method getMobManagerMethod;
    private final Method getActiveMobMethod;
    private final Method getMobTypeMethod;

    // ItemManager methods
    private final Method getItemManagerMethod;
    private final Method getItemStackMethod;

    // APIHelper for mob spawning
    private final Method getAPIHelperMethod;
    private final Method spawnMythicMobMethod;

    private MythicMobsHook(Method instMethod, Method getMobManagerMethod,
                           Method getActiveMobMethod, Method getMobTypeMethod,
                           Method getItemManagerMethod, Method getItemStackMethod,
                           Method getAPIHelperMethod, Method spawnMythicMobMethod) {
        this.instMethod = instMethod;
        this.getMobManagerMethod = getMobManagerMethod;
        this.getActiveMobMethod = getActiveMobMethod;
        this.getMobTypeMethod = getMobTypeMethod;
        this.getItemManagerMethod = getItemManagerMethod;
        this.getItemStackMethod = getItemStackMethod;
        this.getAPIHelperMethod = getAPIHelperMethod;
        this.spawnMythicMobMethod = spawnMythicMobMethod;
    }

    public static MythicMobsHook create(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return null;
        }
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method instMethod = mythicBukkitClass.getMethod("inst");

            // MobManager
            Method getMobManagerMethod = mythicBukkitClass.getMethod("getMobManager");
            Object inst = instMethod.invoke(null);
            Object mobManager = getMobManagerMethod.invoke(inst);
            Method getActiveMobMethod = mobManager.getClass().getMethod("getActiveMob", UUID.class);

            Class<?> activeMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
            Method getMobTypeMethod = activeMobClass.getMethod("getMobType");

            // ItemManager
            Method getItemManagerMethod = mythicBukkitClass.getMethod("getItemManager");
            Object itemManager = getItemManagerMethod.invoke(inst);
            Method getItemStackMethod = itemManager.getClass().getMethod("getItemStack", String.class);

            // APIHelper for spawning
            Method getAPIHelperMethod = mythicBukkitClass.getMethod("getAPIHelper");
            Object apiHelper = getAPIHelperMethod.invoke(inst);
            Method spawnMythicMobMethod = apiHelper.getClass().getMethod("spawnMythicMob", String.class, Location.class);

            return new MythicMobsHook(
                    instMethod, getMobManagerMethod, getActiveMobMethod, getMobTypeMethod,
                    getItemManagerMethod, getItemStackMethod,
                    getAPIHelperMethod, spawnMythicMobMethod
            );
        } catch (Exception e) {
            plugin.getLogger().warning("MythicMobs found but failed to hook API: " + e.getMessage());
            return null;
        }
    }

    public String getMythicMobId(Entity entity) {
        try {
            Object inst = instMethod.invoke(null);
            Object mobManager = getMobManagerMethod.invoke(inst);
            Object result = getActiveMobMethod.invoke(mobManager, entity.getUniqueId());

            if (result instanceof Optional<?> opt && opt.isPresent()) {
                Object activeMob = opt.get();
                Object mobType = getMobTypeMethod.invoke(activeMob);
                return "mythic:" + mobType;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public ItemStack getMythicItem(String itemId) {
        try {
            Object inst = instMethod.invoke(null);
            Object itemManager = getItemManagerMethod.invoke(inst);
            Object result = getItemStackMethod.invoke(itemManager, itemId);
            if (result instanceof ItemStack item) {
                return item;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Entity spawnMythicMob(String mobId, Location location) {
        try {
            Object inst = instMethod.invoke(null);
            Object apiHelper = getAPIHelperMethod.invoke(inst);
            Object result = spawnMythicMobMethod.invoke(apiHelper, mobId, location);
            if (result instanceof Entity entity) {
                return entity;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
