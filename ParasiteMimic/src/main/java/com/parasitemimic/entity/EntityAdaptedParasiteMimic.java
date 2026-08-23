package com.parasitemimic.entity;

import com.parasitemimic.dialogue.MahitoDialogue;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Адаптированный Исказитель — эволюция primitive после 30 убийств.
 * Сильнее, лучше адаптируется к типам урона (как ada_* в SRP).
 */
public class EntityAdaptedParasiteMimic extends EntityParasiteMimic {

    public EntityAdaptedParasiteMimic(World worldIn) {
        super(worldIn);
        this.setSize(0.7F, 1.95F);
        this.experienceValue = 55;
    }

    @Override
    protected ParasiteAdaptation.Profile adaptationProfile() {
        return ParasiteAdaptation.ADAPTED;
    }

    @Override
    protected boolean canEvolve() {
        return false;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(120.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.36D);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(16.0D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(40.0D);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(12.0D);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.65D);
    }

    @Override
    protected void announceSpawn() {
        if (evolvedFromPrimitive) {
            MahitoDialogue.sayNear(this.world, this.posX, this.posY, this.posZ, 28.0D, new String[]{
                    "Биомасса переписала форму. Теперь я... лучше.",
                    "Адаптация завершена. Старая оболочка больше не нужна.",
                    "Тридцать душ. И я наконец стал собой.",
                    "Примитив был черновиком. Это — чистовик."
            });
        } else {
            MahitoDialogue.sayNear(this.world, this.posX, this.posY, this.posZ, 24.0D, new String[]{
                    "Адаптированная биомасса. Вам не понравится этот урок.",
                    "Я уже знаю, чем вы бьёте. Продолжайте — мне любопытно.",
                    "Форма отточена. Души — расходный материал."
            });
        }
    }

    @Override
    public float getEyeHeight() {
        return 1.74F;
    }

    @Override
    public void onDeath(DamageSource cause) {
        if (!this.world.isRemote) {
            MahitoDialogue.sayNear(this.world, this.posX, this.posY, this.posZ, 24.0D, new String[]{
                    "Адаптация... не бесконечна. Занятно.",
                    "Биомасса запомнит этот урон.",
                    "Вы выиграли форму. Душу — нет."
            });
        }
        super.onDeathWithoutSpeech(cause);
    }
}
