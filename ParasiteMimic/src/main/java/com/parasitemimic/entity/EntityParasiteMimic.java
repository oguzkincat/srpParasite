package com.parasitemimic.entity;

import com.parasitemimic.compat.CotesiaCompat;
import com.parasitemimic.compat.SRPCompat;
import com.parasitemimic.dialogue.MahitoDialogue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateClimber;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Исказитель — примитивный паразит-мимик (фаза 4+).
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

    private int doorBreakProgress;
    private BlockPos doorBreakingPos;
    private static final int WOOD_DOOR_BREAK_TICKS = 50;
    private static final int IRON_DOOR_BREAK_TICKS = 160;

    private boolean climbing;

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

    /**
     * Навигатор-лазун: обходит препятствия, ходит через двери, плавает.
     */
    @Override
    protected PathNavigate createNavigator(World worldIn) {
        PathNavigateClimber nav = new PathNavigateClimber(this, worldIn);
        nav.setCanSwim(true);
        nav.setEnterDoors(true);
        nav.setBreakDoors(true);
        return nav;
    }

    @Override
    public boolean isOnLadder() {
        return this.climbing || super.isOnLadder();
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIAttackMelee(this, 1.3D, true));
        this.tasks.addTask(2, new EntityAILeapAtTarget(this, 0.5F));
        this.tasks.addTask(3, new EntityAIWanderAvoidWater(this, 1.0D));
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
        if (!(living instanceof EntityPlayer) && SRPCompat.hasCothAtLeast(living, 2)) {
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
        // Дальше видит цель → лучше строит путь
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
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

        if (this.ticksExisted % 10 == 0) {
            spawnAmbientParticles();
        }

        tryBreakDoors();
        updateWallClimb();

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

        if (!(target instanceof EntityPlayer) && SRPCompat.hasCothAtLeast(target, 2)) {
            this.setAttackTarget(null);
            endStrengthBurst();
            return;
        }

        // Пересчёт короткого пути к цели (обход стен, ям, углов)
        updatePathToTarget(target);

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

        if (leapCooldown <= 0 && distSq > 9.0D && distSq < 64.0D && this.onGround && !this.climbing) {
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

    /**
     * Каждые 15 тиков перестраивает путь к цели, если пути нет
     * или цель далеко — A* обходит препятствия.
     */
    private void updatePathToTarget(EntityLivingBase target) {
        if (this.ticksExisted % 15 != 0) {
            return;
        }
        PathNavigate nav = this.getNavigator();
        double distSq = this.getDistanceSq(target);
        // Если пути нет, путь закончился, или цель заметно сдвинулась — новый маршрут
        if (nav.noPath() || distSq > 2.25D) {
            nav.tryMoveToEntityLiving(target, 1.25D);
        }
    }

    private void updateWallClimb() {
        EntityLivingBase target = this.getAttackTarget();
        boolean wantClimb = false;

        if (target != null && target.isEntityAlive()) {
            double dy = target.posY - this.posY;
            if (dy > 1.8D && this.collidedHorizontally) {
                wantClimb = true;
            }
        }

        this.climbing = wantClimb;

        if (wantClimb) {
            if (this.motionY < 0.25D) {
                this.motionY = 0.25D;
            }
            this.fallDistance = 0.0F;
        }
    }

    private void tryBreakDoors() {
        if (this.world.isRemote) {
            return;
        }

        EntityLivingBase target = this.getAttackTarget();
        if (target == null) {
            resetDoorBreak();
            return;
        }

        EnumFacing facing = this.getHorizontalFacing();
        BlockPos feet = new BlockPos(this.posX, this.posY, this.posZ);
        BlockPos[] candidates = new BlockPos[] {
                feet.offset(facing),
                feet.offset(facing).up(),
                feet.up().offset(facing)
        };

        BlockPos doorPos = null;
        IBlockState doorState = null;
        int breakTicks = -1;
        boolean ironDoor = false;

        for (BlockPos pos : candidates) {
            IBlockState state = this.world.getBlockState(pos);
            Block block = state.getBlock();
            if (!(block instanceof BlockDoor)) {
                continue;
            }
            if (state.getValue(BlockDoor.HALF) != BlockDoor.EnumDoorHalf.LOWER) {
                pos = pos.down();
                state = this.world.getBlockState(pos);
                block = state.getBlock();
                if (!(block instanceof BlockDoor)) {
                    continue;
                }
            }

            Material mat = state.getMaterial();
            if (mat == Material.WOOD) {
                breakTicks = WOOD_DOOR_BREAK_TICKS;
                doorPos = pos;
                doorState = state;
                ironDoor = false;
                break;
            }
            if (block == Blocks.IRON_DOOR || mat == Material.IRON) {
                breakTicks = IRON_DOOR_BREAK_TICKS;
                doorPos = pos;
                doorState = state;
                ironDoor = true;
                break;
            }
        }

        if (doorPos == null || breakTicks < 0) {
            resetDoorBreak();
            return;
        }

        if (this.doorBreakingPos == null || !this.doorBreakingPos.equals(doorPos)) {
            this.doorBreakingPos = doorPos.toImmutable();
            this.doorBreakProgress = 0;
            if (ironDoor) {
                this.playSound(SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.9F, 0.85F);
            } else {
                this.playSound(SoundEvents.ENTITY_ZOMBIE_ATTACK_DOOR_WOOD, 0.9F, 1.0F);
            }
        }

        this.doorBreakProgress++;

        if (this.doorBreakProgress % 10 == 0) {
            float progress = (float) this.doorBreakProgress / (float) breakTicks;
            float pitch = 0.85F + progress * 0.35F + this.rand.nextFloat() * 0.1F;

            if (ironDoor) {
                this.world.playEvent(1020, doorPos, 0);
                this.playSound(SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.85F, pitch);
                this.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.25F, 1.4F + this.rand.nextFloat() * 0.2F);
            } else {
                this.world.playEvent(1019, doorPos, 0);
                this.playSound(SoundEvents.ENTITY_ZOMBIE_ATTACK_DOOR_WOOD, 0.85F, pitch);
            }
        }

        if (this.doorBreakProgress == breakTicks - 15 || this.doorBreakProgress == breakTicks - 5) {
            if (ironDoor) {
                this.playSound(SoundEvents.BLOCK_ANVIL_PLACE, 0.5F, 1.6F);
            } else {
                this.playSound(SoundEvents.BLOCK_WOOD_BREAK, 0.7F, 0.8F);
            }
        }

        if (this.world instanceof WorldServer && this.doorBreakProgress % 5 == 0) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                    doorPos.getX() + 0.5D, doorPos.getY() + 0.5D, doorPos.getZ() + 0.5D,
                    4, 0.25D, 0.25D, 0.25D, 0.05D,
                    Block.getStateId(doorState));
        }

        if (this.doorBreakProgress >= breakTicks) {
            IBlockState lower = this.world.getBlockState(doorPos);
            if (lower.getBlock() instanceof BlockDoor) {
                this.world.setBlockToAir(doorPos.up());
                this.world.setBlockToAir(doorPos);
                this.world.playEvent(1021, doorPos, 0);
                if (ironDoor) {
                    this.playSound(SoundEvents.ENTITY_ZOMBIE_BREAK_DOOR_WOOD, 1.0F, 0.7F);
                    this.playSound(SoundEvents.BLOCK_ANVIL_DESTROY, 0.7F, 1.1F);
                } else {
                    this.playSound(SoundEvents.ENTITY_ZOMBIE_BREAK_DOOR_WOOD, 1.0F, 1.0F);
                    this.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0F, 0.9F);
                }
                if (this.world instanceof WorldServer) {
                    ((WorldServer) this.world).spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL,
                            doorPos.getX() + 0.5D, doorPos.getY() + 1.0D, doorPos.getZ() + 0.5D,
                            6, 0.3D, 0.4D, 0.3D, 0.02D);
                }
            }
            resetDoorBreak();
        }
    }

    private void resetDoorBreak() {
        this.doorBreakProgress = 0;
        this.doorBreakingPos = null;
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
        spawnAdaptParticles();
    }

    private void endStrengthBurst() {
        inStrengthBurst = false;
        strengthBurstTicks = 0;
    }

    private void performRoar() {
        MahitoDialogue.onRoar(this.world, this.posX, this.posY, this.posZ);
        this.playSound(SoundEvents.ENTITY_ENDERDRAGON_GROWL, 0.7F, 1.6F);
        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                    this.posX, this.posY + 1.0D, this.posZ,
                    8, 0.5D, 0.3D, 0.5D, 0.02D);
        }
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
        spawnAdaptParticles();
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
        spawnDeathParticles();
        this.setDead();
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        if (!(target instanceof EntityLivingBase)) {
            return super.attackEntityAsMob(target);
        }
        EntityLivingBase living = (EntityLivingBase) target;

        if (living instanceof EntityPlayer) {
            boolean hit = living.attackEntityFrom(DamageSource.causeMobDamage(this), 10.0F);
            if (hit) {
                living.knockBack(this, inStrengthBurst ? 1.35F : 0.8F,
                        MathHelper.sin(this.rotationYaw * 0.017453292F),
                        -MathHelper.cos(this.rotationYaw * 0.017453292F));
                living.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 120, 1));
                if (inStrengthBurst) {
                    living.addPotionEffect(new PotionEffect(MobEffects.WITHER, 50, 0));
                }
                SRPCompat.applyCoth(living);
                if (this.world instanceof WorldServer) {
                    ((WorldServer) this.world).spawnParticle(EnumParticleTypes.CRIT,
                            living.posX, living.posY + living.height * 0.5D, living.posZ,
                            6, 0.25D, 0.3D, 0.25D, 0.15D);
                    ((WorldServer) this.world).spawnParticle(EnumParticleTypes.REDSTONE,
                            living.posX, living.posY + living.height * 0.5D, living.posZ,
                            4, 0.2D, 0.25D, 0.2D, 0.0D);
                }
                MahitoDialogue.onAttack(this.world, this.posX, this.posY, this.posZ);
            }
            this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 0.85F);
            return hit;
        }

        SRPCompat.applyCoth(living);

        if (living.getHealth() > 10.0F) {
            living.setHealth(10.0F);
            living.knockBack(this, 0.6F,
                    MathHelper.sin(this.rotationYaw * 0.017453292F),
                    -MathHelper.cos(this.rotationYaw * 0.017453292F));
        }

        this.setAttackTarget(null);

        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                    living.posX, living.posY + living.height * 0.5D, living.posZ,
                    10, 0.3D, 0.4D, 0.3D, 0.0D);
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.REDSTONE,
                    living.posX, living.posY + living.height * 0.5D, living.posZ,
                    6, 0.25D, 0.3D, 0.25D, 0.0D);
        }

        MahitoDialogue.onAttack(this.world, this.posX, this.posY, this.posZ);
        this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 0.85F);
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!this.world.isRemote) {
            float before = amount;
            amount = adaptation.apply(source, amount, this.isBurning());
            if (amount < before * 0.95F) {
                spawnAdaptParticles();
            }
            spawnHurtParticles();
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
        if (!this.world.isRemote) {
            spawnDeathParticles();
            if (!(this instanceof EntityAdaptedParasiteMimic)) {
                MahitoDialogue.sayNear(this.world, this.posX, this.posY, this.posZ, 24.0D, new String[]{
                        "Форма разрушена... биомасса ещё здесь.",
                        "Ха... любопытный исход.",
                        "Это ещё не конец искажения.",
                        "Улей заберёт остатки. Не переживай."
                });
            }
        }
        super.onDeath(cause);
    }

    public void onDeathWithoutSpeech(DamageSource cause) {
        if (!this.world.isRemote) {
            spawnDeathParticles();
        }
        super.onDeath(cause);
    }

    private void spawnAmbientParticles() {
        if (!(this.world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) this.world;
        double x = this.posX;
        double y = this.posY + this.height * 0.5D;
        double z = this.posZ;
        server.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 2, 0.25D, 0.4D, 0.25D, 0.0D);
        if (this.rand.nextInt(3) == 0) {
            server.spawnParticle(EnumParticleTypes.PORTAL, x, y, z, 3, 0.3D, 0.5D, 0.3D, 0.15D);
        }
        if (this instanceof EntityAdaptedParasiteMimic && this.rand.nextBoolean()) {
            server.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y + 0.3D, z, 2, 0.2D, 0.3D, 0.2D, 0.0D);
        }
    }

    private void spawnAdaptParticles() {
        if (!(this.world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) this.world;
        double x = this.posX;
        double y = this.posY + this.height * 0.6D;
        double z = this.posZ;
        server.spawnParticle(EnumParticleTypes.SPELL_MOB, x, y, z, 12, 0.4D, 0.5D, 0.4D, 0.0D);
        server.spawnParticle(EnumParticleTypes.CRIT_MAGIC, x, y, z, 8, 0.35D, 0.4D, 0.35D, 0.2D);
        server.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 6, 0.3D, 0.4D, 0.3D, 0.0D);
    }

    private void spawnHurtParticles() {
        if (!(this.world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) this.world;
        server.spawnParticle(EnumParticleTypes.DAMAGE_INDICATOR,
                this.posX, this.posY + this.height * 0.7D, this.posZ,
                4, 0.2D, 0.25D, 0.2D, 0.0D);
        server.spawnParticle(EnumParticleTypes.REDSTONE,
                this.posX, this.posY + this.height * 0.5D, this.posZ,
                5, 0.25D, 0.3D, 0.25D, 0.0D);
    }

    private void spawnDeathParticles() {
        if (!(this.world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) this.world;
        server.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL,
                this.posX, this.posY + 1.0D, this.posZ,
                6, 0.4D, 0.4D, 0.4D, 0.05D);
        server.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                this.posX, this.posY + 0.8D, this.posZ,
                10, 0.35D, 0.4D, 0.35D, 0.02D);
        server.spawnParticle(EnumParticleTypes.REDSTONE,
                this.posX, this.posY + 0.8D, this.posZ,
                15, 0.5D, 0.6D, 0.5D, 0.0D);
        server.spawnParticle(EnumParticleTypes.PORTAL,
                this.posX, this.posY + 0.5D, this.posZ,
                20, 0.4D, 0.6D, 0.4D, 0.3D);
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
