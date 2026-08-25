package com.parasitemimic.entity;

import com.parasitemimic.compat.CotesiaCompat;
import com.parasitemimic.compat.SRPCompat;
import com.parasitemimic.dialogue.MahitoDialogue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Исказитель — примитивный паразит-мимик (фаза 4+).
 * Рождается из слияния движущейся плоти, как остальные primitive.
 */
public class EntityParasiteMimic extends EntityMob {

    private int leapCooldown;
    private int roarCooldown;
    private int strengthBurstCooldown;
    private int strengthBurstTicks;
    private boolean inStrengthBurst;
    private boolean announcedSpawn;
    private boolean fromMovingFleshMerge;
    protected boolean evolvedFromPrimitive;
    private int killCount;
    private final ParasiteAdaptation adaptation = new ParasiteAdaptation(adaptationProfile());

    public EntityParasiteMimic(World worldIn) {
        super(worldIn);
        this.setSize(0.6F, 1.8F);
        this.experienceValue = 30;
    }

    protected ParasiteAdaptation.Profile adaptationProfile() {
        return ParasiteAdaptation.PRIMITIVE;
    }

    protected boolean canEvolve() {
        return true;
    }

    public void setFromMovingFleshMerge() {
        this.fromMovingFleshMerge = true;
    }

    public void setEvolvedFromPrimitive() {
        this.evolvedFromPrimitive = true;
        this.announcedSpawn = false;
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIAttackMelee(this, 1.3D, true));
        this.tasks.addTask(2, new EntityAILeapAtTarget(this, 0.5F));
        this.tasks.addTask(3, new EntityAIWanderAvoidWater(this, 0.95D));
        this.tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        this.tasks.addTask(5, new EntityAILookIdle(this));

        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
        this.targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(this, EntityLivingBase.class, 10, true, false,
                living -> living != null
                        && !(living instanceof EntityParasiteMimic)
                        && living.isEntityAlive()
                        && shouldTarget(living)));
    }

    private boolean shouldTarget(EntityLivingBase living) {
        if (living instanceof EntityParasiteMimic) {
            return false;
        }
        if (SRPCompat.isParasiteEntity(living) || SRPCompat.isMovingFlesh(living)) {
            return false;
        }
        if (living instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) living;
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            return !CotesiaCompat.isPlayerParasite(player);
        }
        return true;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(70.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.33D);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(10.0D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(36.0D);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(7.0D);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.4D);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();

        if (this.world.isRemote) {
            return;
        }

        if (!announcedSpawn) {
            announcedSpawn = true;
            announceSpawn();
        }

        if (leapCooldown > 0) leapCooldown--;
        if (roarCooldown > 0) roarCooldown--;
        if (strengthBurstCooldown > 0) strengthBurstCooldown--;

        if (this.ticksExisted % 40 == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(1.5F);
        }

        MahitoDialogue.onIdle(this.world, this.posX, this.posY, this.posZ);

        EntityLivingBase target = this.getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            endStrengthBurst();
            return;
        }

        double distSq = this.getDistanceSq(target);

        if (strengthBurstCooldown <= 0 && distSq < 100.0D) {
            startStrengthBurst();
        }
        if (inStrengthBurst) {
            strengthBurstTicks--;
            if (strengthBurstTicks <= 0) {
                endStrengthBurst();
            }
        }

        if (leapCooldown <= 0 && distSq > 9.0D && distSq < 64.0D && this.onGround) {
            double dx = target.posX - this.posX;
            double dz = target.posZ - this.posZ;
            this.motionX += dx * 0.18D;
            this.motionY += 0.45D;
            this.motionZ += dz * 0.18D;
            this.leapCooldown = 70;
            this.playSound(SoundEvents.ENTITY_ENDERDRAGON_FLAP, 0.6F, 1.4F);
        }

        if (roarCooldown <= 0 && distSq < 81.0D) {
            performRoar();
            this.roarCooldown = 160;
        }
    }

    private void startStrengthBurst() {
        inStrengthBurst = true;
        strengthBurstTicks = 90;
        strengthBurstCooldown = 200;
        this.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 90, 1, false, false));
        this.addPotionEffect(new PotionEffect(MobEffects.SPEED, 90, 1, false, false));
        this.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 90, 0, false, false));
        MahitoDialogue.onStrengthBurst(this.world, this.posX, this.posY, this.posZ);
        this.playSound(SoundEvents.ENTITY_WITHER_AMBIENT, 0.8F, 1.5F);
    }

    private void endStrengthBurst() {
        inStrengthBurst = false;
        strengthBurstTicks = 0;
    }

    private void performRoar() {
        MahitoDialogue.onRoar(this.world, this.posX, this.posY, this.posZ);
        this.playSound(SoundEvents.ENTITY_ENDERDRAGON_GROWL, 0.7F, 1.6F);
        for (EntityLivingBase nearby : this.world.getEntitiesWithinAABB(EntityLivingBase.class,
                this.getEntityBoundingBox().grow(8.0D))) {
            if (nearby != this && shouldTarget(nearby) && nearby.isEntityAlive()) {
                nearby.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 70, 1));
                nearby.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 50, 0));
                if (nearby instanceof EntityPlayer) {
                    nearby.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 40, 0));
                }
            }
        }
    }

    protected void announceSpawn() {
        if (fromMovingFleshMerge) {
            MahitoDialogue.onSpawnFromFlesh(this.world, this.posX, this.posY, this.posZ);
        } else {
            MahitoDialogue.onSpawn(this.world, this.posX, this.posY, this.posZ);
        }
    }

    @Override
    public void onKillEntity(EntityLivingBase entityLivingIn) {
        super.onKillEntity(entityLivingIn);
        if (this.world.isRemote || !canEvolve()) {
            return;
        }
        killCount++;
        if (killCount >= 30) {
            evolveToAdapted();
        }
    }

    private void evolveToAdapted() {
        if (this.world.isRemote || this.isDead) {
            return;
        }
        EntityAdaptedParasiteMimic adapted = new EntityAdaptedParasiteMimic(this.world);
        adapted.setLocationAndAngles(this.posX, this.posY, this.posZ, this.rotationYaw, this.rotationPitch);
        adapted.setEvolvedFromPrimitive();
        float ratio = this.getHealth() / this.getMaxHealth();
        adapted.setHealth(Math.max(adapted.getMaxHealth() * 0.5F, adapted.getMaxHealth() * ratio));
        this.world.spawnEntity(adapted);
        this.world.playSound(null, this.posX, this.posY, this.posZ,
                SoundEvents.ENTITY_WITHER_SPAWN, this.getSoundCategory(), 0.8F, 1.4F);
        this.setDead();
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        if (inStrengthBurst) {
            damage *= 1.6F;
        }

        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), damage);

        if (hit && target instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) target;

            living.knockBack(this, inStrengthBurst ? 1.35F : 0.8F,
                    MathHelper.sin(this.rotationYaw * 0.017453292F),
                    -MathHelper.cos(this.rotationYaw * 0.017453292F));

            living.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 120, 1));
            living.addPotionEffect(new PotionEffect(MobEffects.POISON, 80, 0));
            if (inStrengthBurst) {
                living.addPotionEffect(new PotionEffect(MobEffects.WITHER, 50, 0));
            }

            // Зов улья SRP → ассимиляция
            SRPCompat.applyCoth(living);

            MahitoDialogue.onAttack(this.world, this.posX, this.posY, this.posZ);
        }

        this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 0.85F);
        return hit;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        // Адаптация к урону (как у паразитов SRP)
        if (!this.world.isRemote) {
            amount = adaptation.apply(source, amount, this.isBurning());
        }
        if (!source.isFireDamage() && !source.canHarmInCreative()) {
            amount *= 0.7F;
        }
        boolean result = super.attackEntityFrom(source, amount);
        if (result && !this.world.isRemote) {
            MahitoDialogue.onHurt(this.world, this.posX, this.posY, this.posZ);
        }
        return result;
    }

    @Override
    public void onDeath(DamageSource cause) {
        if (!this.world.isRemote && !(this instanceof EntityAdaptedParasiteMimic)) {
            MahitoDialogue.sayNear(this.world, this.posX, this.posY, this.posZ, 24.0D, new String[]{
                    "Форма разрушена... биомасса ещё здесь.",
                    "Ха... любопытный исход.",
                    "Это ещё не конец искажения.",
                    "Улей заберёт остатки. Не переживай."
            });
        }
        super.onDeath(cause);
    }

    public void onDeathWithoutSpeech(DamageSource cause) {
        super.onDeath(cause);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean("FromMovingFleshMerge", fromMovingFleshMerge);
        compound.setBoolean("AnnouncedSpawn", announcedSpawn);
        compound.setBoolean("EvolvedFromPrimitive", evolvedFromPrimitive);
        compound.setInteger("KillCount", killCount);
        adaptation.writeToNBT(compound);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        fromMovingFleshMerge = compound.getBoolean("FromMovingFleshMerge");
        announcedSpawn = compound.getBoolean("AnnouncedSpawn");
        evolvedFromPrimitive = compound.getBoolean("EvolvedFromPrimitive");
        killCount = compound.getInteger("KillCount");
        adaptation.readFromNBT(compound);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.ENTITY_ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ZOMBIE_DEATH;
    }

    @Override
    public float getEyeHeight() {
        return 1.62F;
    }

    @Override
    public int getMaxSpawnedInChunk() {
        return 1;
    }

    public boolean isInStrengthBurst() {
        return inStrengthBurst;
    }
}
