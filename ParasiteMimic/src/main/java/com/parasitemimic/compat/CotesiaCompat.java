package com.parasitemimic.compat;

import com.parasitemimic.ParasiteMimic;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Optional Cotesia Glomerata integration ({@code after:srpcotesia}).
 * Detects Vagrant / parasite-players so Исказитель treats them as hive-kin.
 */
public final class CotesiaCompat {

    public static final String COTESIA_MODID = "srpcotesia";

    private static boolean loaded;
    private static Method parasiteCheck;
    private static boolean parasiteCheckStatic;
    private static boolean reflectionFailed;

    private CotesiaCompat() {}

    public static void init() {
        loaded = Loader.isModLoaded(COTESIA_MODID);
        if (!loaded) {
            ParasiteMimic.logger.info("Cotesia Glomerata not present — Vagrant integration off.");
            return;
        }
        resolveReflection();
        ParasiteMimic.logger.info("Cotesia Glomerata integration ON (Vagrant detection {}).",
                parasiteCheck != null ? "reflection" : "potion/NBT fallback");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * True if the player is currently a Cotesia parasite (Vagrant), not hidden as human.
     */
    public static boolean isPlayerParasite(EntityPlayer player) {
        if (!loaded || player == null) {
            return false;
        }
        Boolean reflected = invokeParasiteCheck(player);
        if (reflected != null) {
            return reflected;
        }
        if (hasCotesiaPotion(player)) {
            return true;
        }
        return hasCotesiaNbt(player);
    }

    private static Boolean invokeParasiteCheck(EntityPlayer player) {
        if (reflectionFailed || parasiteCheck == null) {
            return null;
        }
        try {
            Object result = parasiteCheckStatic
                    ? parasiteCheck.invoke(null, player)
                    : parasiteCheck.invoke(parasiteCheck.getDeclaringClass(), player);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable t) {
            reflectionFailed = true;
            ParasiteMimic.logger.warn("Cotesia parasite check failed: {}", t.toString());
        }
        return null;
    }

    private static boolean hasCotesiaPotion(EntityPlayer player) {
        Collection<PotionEffect> effects = player.getActivePotionEffects();
        for (PotionEffect effect : effects) {
            Potion potion = effect.getPotion();
            ResourceLocation id = Potion.REGISTRY.getNameForObject(potion);
            if (id != null && COTESIA_MODID.equals(id.getResourceDomain())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCotesiaNbt(EntityPlayer player) {
        if (player.getEntityData() == null) {
            return false;
        }
        net.minecraft.nbt.NBTTagCompound data = player.getEntityData();
        if (data.getBoolean("srpcotesia_parasite") || data.getBoolean("cotesia_parasite")
                || data.getBoolean("isVagrant") || data.getBoolean("IsParasite")) {
            return true;
        }
        if (data.hasKey("ForgeCaps")) {
            String caps = data.getCompoundTag("ForgeCaps").toString().toLowerCase();
            return caps.contains("cotesia") && (caps.contains("parasite") || caps.contains("vagrant"));
        }
        return false;
    }

    private static void resolveReflection() {
        String[] classNames = {
                "srpcotesia.util.PlayerParasiteHelper",
                "srpcotesia.capability.ParasitePlayer",
                "com.roguetictac.cotesia.util.ParasiteHelper",
                "com.roguetictac.srpcotesia.util.ParasiteHelper",
                "srpcotesia.handlers.ParasiteHandler",
                "srpcotesia.player.ParasitePlayerData"
        };
        String[] methodNames = {
                "isParasite", "isPlayerParasite", "isVagrant", "isTransformed", "getIsParasite"
        };
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                for (String methodName : methodNames) {
                    try {
                        Method method = clazz.getMethod(methodName, EntityPlayer.class);
                        if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                            continue;
                        }
                        parasiteCheck = method;
                        parasiteCheckStatic = true;
                        ParasiteMimic.logger.info("Cotesia detect via {}.{}", className, methodName);
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
