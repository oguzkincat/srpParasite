package com.parasitemimic.entity;

import com.parasitemimic.ParasiteMimic;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public final class ModEntities {

    private static int id;

    private ModEntities() {}

    public static void register() {
        registerEntity("parasite_mimic", EntityParasiteMimic.class, 80, 3, true, 0x1a0a1a, 0x6b2d7a);
        registerEntity("adapted_parasite_mimic", EntityAdaptedParasiteMimic.class, 80, 3, true, 0x2a0508, 0xc4282d);
    }

    private static void registerEntity(String name, Class<? extends Entity> clazz,
                                       int trackingRange, int updateFrequency, boolean sendsVelocity,
                                       int eggPrimary, int eggSecondary) {
        ResourceLocation rl = new ResourceLocation(ParasiteMimic.MODID, name);
        EntityRegistry.registerModEntity(rl, clazz, ParasiteMimic.MODID + "." + name, id++,
                ParasiteMimic.instance, trackingRange, updateFrequency, sendsVelocity, eggPrimary, eggSecondary);
    }
}
