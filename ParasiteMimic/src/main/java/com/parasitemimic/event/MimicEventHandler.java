package com.parasitemimic.event;

import com.parasitemimic.compat.SRPCompat;
import com.parasitemimic.dialogue.MahitoDialogue;
import com.parasitemimic.entity.EntityAdaptedParasiteMimic;
import com.parasitemimic.entity.EntityParasiteMimic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public class MimicEventHandler {

    public MimicEventHandler() {
        Biome[] biomes = new Biome[]{
                Biomes.PLAINS,
                Biomes.FOREST,
                Biomes.ROOFED_FOREST,
                Biomes.SWAMPLAND,
                Biomes.TAIGA,
                Biomes.SAVANNA,
                Biomes.EXTREME_HILLS,
                Biomes.DESERT,
                Biomes.JUNGLE
        };
        for (Biome biome : biomes) {
            if (biome != null) {
                EntityRegistry.addSpawn(EntityParasiteMimic.class, 2, 1, 1, EnumCreatureType.MONSTER, biome);
                EntityRegistry.addSpawn(EntityAdaptedParasiteMimic.class, 1, 1, 1, EnumCreatureType.MONSTER, biome);
            }
        }
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!(event.getEntityLiving() instanceof EntityParasiteMimic)) {
            return;
        }
        World world = event.getWorld();
        if (SRPCompat.isLoaded()) {
            boolean adapted = event.getEntityLiving() instanceof EntityAdaptedParasiteMimic;
            if (adapted && !SRPCompat.isAdaptedPhase(world)) {
                event.setResult(Event.Result.DENY);
                return;
            }
            if (!adapted && !SRPCompat.isPrimitivePhase(world)) {
                event.setResult(Event.Result.DENY);
                return;
            }
        }
        BlockPos pos = new BlockPos(event.getX(), event.getY(), event.getZ());
        int light = world.getLight(pos);
        if (light > 8) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** SRP-паразиты не должны держать Исказителя как цель. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSetAttackTarget(LivingSetAttackTargetEvent event) {
        EntityLivingBase target = event.getTarget();
        if (!(target instanceof EntityParasiteMimic)) {
            return;
        }
        EntityLivingBase attacker = event.getEntityLiving();
        if (isHiveAlly(attacker) && attacker instanceof EntityLiving) {
            ((EntityLiving) attacker).setAttackTarget(null);
        }
    }

    /** Если AI SRP снова нацелился — сбрасываем каждый тик. */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (!(entity instanceof EntityLiving) || entity.world.isRemote) {
            return;
        }
        EntityLiving living = (EntityLiving) entity;
        EntityLivingBase target = living.getAttackTarget();
        if (target instanceof EntityParasiteMimic && isHiveAlly(living)) {
            living.setAttackTarget(null);
        }
    }

    /** Отмена атаки от паразитов / других Исказителей. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityParasiteMimic)) {
            return;
        }
        Entity source = event.getSource().getTrueSource();
        if (source == null) {
            source = event.getSource().getImmediateSource();
        }
        if (source instanceof EntityLivingBase && isHiveAlly((EntityLivingBase) source)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityParasiteMimic)) {
            return;
        }
        Entity source = event.getSource().getTrueSource();
        if (source == null) {
            source = event.getSource().getImmediateSource();
        }
        if (source instanceof EntityLivingBase && isHiveAlly((EntityLivingBase) source)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase dead = event.getEntityLiving();
        World world = dead.getEntityWorld();
        if (world.isRemote) {
            return;
        }

        if (dead instanceof EntityPlayer) {
            EntityLivingBase source = null;
            if (event.getSource().getTrueSource() instanceof EntityLivingBase) {
                source = (EntityLivingBase) event.getSource().getTrueSource();
            }
            if (source instanceof EntityParasiteMimic) {
                MahitoDialogue.onKill(world, dead.posX, dead.posY, dead.posZ);
            }
        }
    }

    private static boolean isHiveAlly(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof EntityParasiteMimic) {
            return true;
        }
        return SRPCompat.isParasiteEntity(entity) || SRPCompat.isMovingFlesh(entity);
    }
}
