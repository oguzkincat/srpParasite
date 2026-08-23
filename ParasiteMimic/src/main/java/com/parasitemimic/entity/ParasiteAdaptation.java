package com.parasitemimic.entity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Random;

/**
 * SRP-style damage adaptation: learn damage sources, stack resistance, cap by tier.
 */
public final class ParasiteAdaptation {

    public static final Profile PRIMITIVE = new Profile(5, 0.05F, 0.60F, 0.70F, 0.70F);
    public static final Profile ADAPTED = new Profile(8, 0.10F, 0.80F, 0.85F, 0.40F);

    private static final Random RNG = new Random();

    private final Profile profile;
    private final LinkedHashMap<String, Integer> stacks = new LinkedHashMap<String, Integer>();

    public ParasiteAdaptation(Profile profile) {
        this.profile = profile;
    }

    public float apply(DamageSource source, float amount, boolean burning) {
        if (amount <= 0.0F || source == null || !canAdapt(source)) {
            return amount;
        }
        String key = keyOf(source);
        if (key == null) {
            return amount;
        }

        boolean failFromFire = burning && RNG.nextFloat() < profile.fireFailChance;
        if (!failFromFire && RNG.nextFloat() < profile.learnChance) {
            learn(key);
        }

        Integer current = stacks.get(key);
        if (current == null || current <= 0) {
            return amount;
        }
        float reduction = Math.min(profile.cap, current * profile.perHit);
        return amount * (1.0F - reduction);
    }

    public int getStacks(String key) {
        Integer v = stacks.get(key);
        return v == null ? 0 : v;
    }

    public void writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (java.util.Map.Entry<String, Integer> e : stacks.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("K", e.getKey());
            tag.setInteger("V", e.getValue());
            list.appendTag(tag);
        }
        compound.setTag("AdaptStacks", list);
    }

    public void readFromNBT(NBTTagCompound compound) {
        stacks.clear();
        NBTTagList list = compound.getTagList("AdaptStacks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            stacks.put(tag.getString("K"), tag.getInteger("V"));
        }
    }

    private void learn(String key) {
        Integer current = stacks.get(key);
        if (current == null) {
            if (stacks.size() >= profile.maxTypes) {
                Iterator<String> it = stacks.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            stacks.put(key, 1);
            return;
        }
        int maxStacks = Math.max(1, Math.round(profile.cap / profile.perHit));
        stacks.put(key, Math.min(maxStacks, current + 1));
    }

    private static boolean canAdapt(DamageSource source) {
        if (source.canHarmInCreative()) {
            return false;
        }
        String type = source.getDamageType();
        return !"inWall".equals(type)
                && !"drown".equals(type)
                && !"outOfWorld".equals(type)
                && !"starve".equals(type);
    }

    private static String keyOf(DamageSource source) {
        if (source.getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) source.getTrueSource();
            ItemStack held = player.getHeldItemMainhand();
            if (!held.isEmpty()) {
                ResourceLocation id = Item.REGISTRY.getNameForObject(held.getItem());
                if (id != null) {
                    return "item:" + id.toString();
                }
            }
            return "player";
        }
        return source.getDamageType() == null ? "generic" : source.getDamageType();
    }

    public static final class Profile {
        public final int maxTypes;
        public final float perHit;
        public final float cap;
        public final float learnChance;
        public final float fireFailChance;

        public Profile(int maxTypes, float perHit, float cap, float learnChance, float fireFailChance) {
            this.maxTypes = maxTypes;
            this.perHit = perHit;
            this.cap = cap;
            this.learnChance = learnChance;
            this.fireFailChance = fireFailChance;
        }
    }
}
