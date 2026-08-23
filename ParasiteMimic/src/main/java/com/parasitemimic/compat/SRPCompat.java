package com.parasitemimic.compat;

import com.parasitemimic.ParasiteMimic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Required Scape and Run: Parasites integration (1.9.x / 1.9.21).
 * Classes are accessed by reflection so this mod still compiles without the SRP jar.
 * At runtime Forge will refuse to load without srparasites ({@code required-after:srparasites}).
 */
public final class SRPCompat {

    public static final String SRP_MODID = "srparasites";
    public static final int PRIMITIVE_PHASE = 4;
    public static final int ADAPTED_PHASE = 6;

    private static final Set<String> MOVING_FLESH_IDS = new HashSet<String>(Arrays.asList(
            "srparasites:movingflesh",
            "srparasites:moving_flesh",
            "srparasites:movflesh"
    ));

    private static final Set<String> PRIMITIVE_IDS = new HashSet<String>(Arrays.asList(
            "srparasites:pri_longarms",
            "srparasites:pri_manducater",
            "srparasites:pri_reeker",
            "srparasites:pri_yelloweye",
            "srparasites:pri_summoner",
            "srparasites:pri_bolster",
            "srparasites:pri_arachnida",
            "srparasites:pri_devourer",
            "srparasites:pri_tozoon",
            "srparasites:pri_vermin"
    ));

    private static boolean loaded;
    private static boolean phaseLookupFailed;
    private static Method saveDataGet;
    private static Method evolutionPhaseMethod;
    private static boolean evolutionPhaseTakesDim;

    private SRPCompat() {}

    public static void init() {
        loaded = Loader.isModLoaded(SRP_MODID);
        if (!loaded) {
            return;
        }
        resolvePhaseReflection();
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean isMovingFlesh(Entity entity) {
        ResourceLocation key = keyOf(entity);
        return key != null && MOVING_FLESH_IDS.contains(key.toString().toLowerCase());
    }

    public static boolean isPrimitiveParasite(Entity entity) {
        ResourceLocation key = keyOf(entity);
        if (key == null) {
            return false;
        }
        String id = key.toString().toLowerCase();
        return PRIMITIVE_IDS.contains(id) || id.startsWith("srparasites:pri_");
    }

    public static boolean isParasiteEntity(Entity entity) {
        ResourceLocation key = keyOf(entity);
        return key != null && SRP_MODID.equals(key.getResourceDomain());
    }

    /**
     * Current SRP evolution phase for this world/dimension.
     * Returns 0 if phase cannot be read.
     */
    public static int getEvolutionPhase(World world) {
        if (world == null) {
            return 0;
        }
        if (!loaded) {
            loaded = Loader.isModLoaded(SRP_MODID);
        }
        if (!loaded || phaseLookupFailed || saveDataGet == null || evolutionPhaseMethod == null) {
            return 0;
        }
        try {
            Object data = saveDataGet.invoke(null, world);
            if (data == null) {
                return 0;
            }
            Object result = evolutionPhaseTakesDim
                    ? evolutionPhaseMethod.invoke(data, world.provider.getDimension())
                    : evolutionPhaseMethod.invoke(data);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable t) {
            if (!phaseLookupFailed) {
                phaseLookupFailed = true;
                ParasiteMimic.logger.warn("Could not read SRP evolution phase: {}", t.toString());
            }
        }
        return 0;
    }

    public static boolean isPrimitivePhase(World world) {
        if (phaseLookupFailed) {
            return true;
        }
        return getEvolutionPhase(world) >= PRIMITIVE_PHASE;
    }

    public static boolean isAdaptedPhase(World world) {
        if (phaseLookupFailed) {
            return true;
        }
        return getEvolutionPhase(world) >= ADAPTED_PHASE;
    }

    private static void resolvePhaseReflection() {
        String[] classNames = {
                "com.dhanantry.scapeandrunparasites.world.SRPSaveData",
                "com.dhanantry.scapeandrunparasites.world.SRPWorldData",
                "com.dhanantry.scapeandrunparasites.util.SRPSaveData"
        };
        String[] methodNames = {
                "getEvolutionPhase",
                "getEvolutionParasiteStage",
                "getPhase",
                "getParasiteEvolutionPhase"
        };

        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                try {
                    saveDataGet = clazz.getMethod("get", World.class);
                } catch (NoSuchMethodException e) {
                    saveDataGet = clazz.getMethod("get", net.minecraft.world.World.class);
                }

                for (String methodName : methodNames) {
                    try {
                        evolutionPhaseMethod = clazz.getMethod(methodName, int.class);
                        evolutionPhaseTakesDim = true;
                        ParasiteMimic.logger.info("SRP phase via {}.{}(int)", className, methodName);
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                    try {
                        evolutionPhaseMethod = clazz.getMethod(methodName);
                        evolutionPhaseTakesDim = false;
                        ParasiteMimic.logger.info("SRP phase via {}.{}()", className, methodName);
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        phaseLookupFailed = true;
        ParasiteMimic.logger.warn("SRP is loaded but evolution phase method was not found. Merge spawn will still run.");
    }

    private static ResourceLocation keyOf(Entity entity) {
        if (!loaded || entity == null) {
            return null;
        }
        return EntityList.getKey(entity);
    }
}
