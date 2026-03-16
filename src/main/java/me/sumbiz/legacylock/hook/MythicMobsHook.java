package me.sumbiz.legacylock.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class MythicMobsHook {

    private final Method instMethod;
    private final Method getMobManagerMethod;
    private final Method getActiveMobMethod;
    private final Method getMobTypeMethod;

    private MythicMobsHook(Method instMethod, Method getMobManagerMethod,
                           Method getActiveMobMethod, Method getMobTypeMethod) {
        this.instMethod = instMethod;
        this.getMobManagerMethod = getMobManagerMethod;
        this.getActiveMobMethod = getActiveMobMethod;
        this.getMobTypeMethod = getMobTypeMethod;
    }

    public static MythicMobsHook create(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return null;
        }
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method instMethod = mythicBukkitClass.getMethod("inst");
            Method getMobManagerMethod = mythicBukkitClass.getMethod("getMobManager");

            Object inst = instMethod.invoke(null);
            Object mobManager = getMobManagerMethod.invoke(inst);

            Method getActiveMobMethod = mobManager.getClass().getMethod("getActiveMob", UUID.class);

            Class<?> activeMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
            Method getMobTypeMethod = activeMobClass.getMethod("getMobType");

            return new MythicMobsHook(instMethod, getMobManagerMethod, getActiveMobMethod, getMobTypeMethod);
        } catch (Exception e) {
            plugin.getLogger().warning("MythicMobs found but failed to hook API: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
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
}
