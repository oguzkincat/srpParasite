package com.parasitemimic.event;

import com.parasitemimic.ParasiteMimic;
import com.parasitemimic.compat.SRPCompat;
import com.parasitemimic.entity.EntityParasiteMimic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * SRP 1.9.21: four Moving Flesh merge into a random Primitive at 50% HP.
 * Исказитель is one of those primitives, but only from evolution phase 4.
 */
public class SRPMergeHandler {

    private static final Random RNG = new Random();
    /** ~1 of ~8 primitives from a given merge. */
    private static final float REPLACE_CHANCE = 0.125F;
    private static final double MERGE_RADIUS = 8.0D;
    private static final int FLESH_MEMORY_TICKS = 40;
    private static final int MIN_FLESH_FOR_MERGE = 3;

    private final Map<MergeMark, Integer> recentFleshDeaths = new LinkedHashMap<MergeMark, Integer>();
    private int worldTick;
    private int lastSpawnTick = -9999;

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!SRPCompat.isLoaded()) {
            return;
        }
        EntityLivingBase dead = event.getEntityLiving();
        World world = dead.getEntityWorld();
        if (world.isRemote || !SRPCompat.isMovingFlesh(dead)) {
            return;
        }
        if (!SRPCompat.isPrimitivePhase(world)) {
            return;
        }

        boolean playerKill = event.getSource().getTrueSource() instanceof EntityPlayer;
        if (playerKill) {
            return;
        }

        recentFleshDeaths.put(new MergeMark(world.provider.getDimension(), dead.getPosition()), worldTick);

        int nearbyFlesh = countNearbyMovingFlesh(world, dead, MERGE_RADIUS);
        // 4 flesh merge: the dying one + 3 still present, or several dying together.
        if (nearbyFlesh + 1 >= MIN_FLESH_FOR_MERGE && RNG.nextFloat() < REPLACE_CHANCE) {
            spawnFromFlesh(world, dead.getPosition());
        }
    }

    /**
     * When SRP itself spawns a primitive from a merge, sometimes replace it
     * with Исказитель (same 50% HP rule as other primitives).
     */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (!SRPCompat.isLoaded() || event.getWorld().isRemote) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof EntityParasiteMimic || !SRPCompat.isPrimitiveParasite(entity)) {
            return;
        }
        World world = event.getWorld();
        if (!SRPCompat.isPrimitivePhase(world)) {
            return;
        }
        if (!isRecentMergeNearby(world, entity.getPosition())) {
            return;
        }
        if (RNG.nextFloat() > REPLACE_CHANCE) {
            return;
        }

        entity.setDead();
        spawnFromFlesh(world, entity.getPosition());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        worldTick++;
        if (worldTick % 20 != 0) {
            return;
        }
        Iterator<Map.Entry<MergeMark, Integer>> it = recentFleshDeaths.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<MergeMark, Integer> entry = it.next();
            if (worldTick - entry.getValue() > FLESH_MEMORY_TICKS) {
                it.remove();
            }
        }
    }

    private boolean isRecentMergeNearby(World world, BlockPos pos) {
        int dim = world.provider.getDimension();
        for (Map.Entry<MergeMark, Integer> entry : recentFleshDeaths.entrySet()) {
            MergeMark mark = entry.getKey();
            if (mark.dimension != dim) {
                continue;
            }
            if (worldTick - entry.getValue() > FLESH_MEMORY_TICKS) {
                continue;
            }
            if (mark.pos.distanceSq(pos) <= MERGE_RADIUS * MERGE_RADIUS) {
                return true;
            }
        }
        return countNearbyMovingFlesh(world, pos, MERGE_RADIUS) >= 1;
    }

    private int countNearbyMovingFlesh(World world, Entity origin, double radius) {
        AxisAlignedBB box = origin.getEntityBoundingBox().grow(radius);
        int count = 0;
        for (Entity other : world.getEntitiesWithinAABB(Entity.class, box)) {
            if (other != origin && other.isEntityAlive() && SRPCompat.isMovingFlesh(other)) {
                count++;
            }
        }
        return count;
    }

    private int countNearbyMovingFlesh(World world, BlockPos pos, double radius) {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(radius);
        int count = 0;
        for (Entity other : world.getEntitiesWithinAABB(Entity.class, box)) {
            if (other.isEntityAlive() && SRPCompat.isMovingFlesh(other)) {
                count++;
            }
        }
        return count;
    }

    private void spawnFromFlesh(World world, BlockPos pos) {
        if (worldTick - lastSpawnTick < 30) {
            return;
        }
        EntityParasiteMimic mimic = new EntityParasiteMimic(world);
        mimic.setPosition(pos.getX() + 0.5D, pos.getY() + 0.1D, pos.getZ() + 0.5D);
        mimic.setFromMovingFleshMerge();
        // Same rule as SRP primitives born from Moving Flesh: half health.
        mimic.setHealth(mimic.getMaxHealth() * 0.5F);
        if (world.spawnEntity(mimic)) {
            lastSpawnTick = worldTick;
            ParasiteMimic.logger.info("Исказитель emerged from Moving Flesh merge at {} (phase {})",
                    pos, SRPCompat.getEvolutionPhase(world));
        }
    }

    private static final class MergeMark {
        private final int dimension;
        private final BlockPos pos;

        private MergeMark(int dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos.toImmutable();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MergeMark)) return false;
            MergeMark other = (MergeMark) o;
            return dimension == other.dimension && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return 31 * dimension + pos.hashCode();
        }
    }
}
