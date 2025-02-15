package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.AlexsMobs;
import com.github.alexthe666.alexsmobs.config.AMConfig;
import com.github.alexthe666.alexsmobs.entity.ai.AnimalAIWanderRanged;
import com.github.alexthe666.alexsmobs.item.AMItemRegistry;
import com.github.alexthe666.alexsmobs.misc.AMSoundRegistry;
import com.github.alexthe666.alexsmobs.misc.AMTagRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class EntityRoadrunner extends Animal {

    public float oFlapSpeed;
    public float oFlap;
    public float wingRotDelta = 1.0F;
    public float wingRotation;
    public float destPos;
    public float prevAttackProgress;
    public float attackProgress;
    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(EntityRoadrunner.class, EntityDataSerializers.INT);
    public int timeUntilNextFeather = this.random.nextInt(24000) + 24000;
    private boolean hasMeepSpeed = false;

    protected EntityRoadrunner(EntityType type, Level worldIn) {
        super(type, worldIn);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.1D));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.1D, Ingredient.of(AMTagRegistry.ROADRUNNER_BREEDABLES), false));
        this.goalSelector.addGoal(5, new AnimalAIWanderRanged(this, 50, 1.0D, 25, 7));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, EntityRattlesnake.class, 55, true, true, null));
        this.targetSelector.addGoal(2, (new HurtByTargetGoal(this, EntityRattlesnake.class, Player.class)).setAlertOthers());
    }
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("FeatherTime")) {
            this.timeUntilNextFeather = compound.getInt("FeatherTime");
        }

    }

    public boolean checkSpawnRules(LevelAccessor worldIn, MobSpawnType spawnReasonIn) {
        return AMEntityRegistry.rollSpawn(AMConfig.roadrunnerSpawnRolls, this.getRandom(), spawnReasonIn);
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("FeatherTime", this.timeUntilNextFeather);
    }

    protected SoundEvent getAmbientSound() {
        return isMeep() || random.nextInt(2000) == 0 ? AMSoundRegistry.ROADRUNNER_MEEP.get() : AMSoundRegistry.ROADRUNNER_IDLE.get();
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return AMSoundRegistry.ROADRUNNER_HURT.get();
    }

    protected SoundEvent getDeathSound() {
        return AMSoundRegistry.ROADRUNNER_HURT.get();
    }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ATTACK_TICK, 0);

    }

    public boolean doHurtTarget(Entity entityIn) {
        this.entityData.set(ATTACK_TICK, 5);
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.CACTUS) || source.getMsgId().equals("anvil") || super.isInvulnerableTo(source);
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8.0D).add(Attributes.ATTACK_DAMAGE, 1.0D).add(Attributes.MOVEMENT_SPEED, 0.45F).add(Attributes.FOLLOW_RANGE, 10D);
    }

    public void aiStep() {
        super.aiStep();
        this.oFlap = this.wingRotation;
        this.prevAttackProgress = attackProgress;
        this.oFlapSpeed = this.destPos;
        this.destPos = (float) ((double) this.destPos + (double) (this.onGround() ? -1 : 4) * 0.3D);
        this.destPos = Mth.clamp(this.destPos, 0.0F, 1.0F);
        if (!this.onGround() && this.wingRotDelta < 1.0F) {
            this.wingRotDelta = 1.0F;
        }
        if (!this.level().isClientSide && this.isAlive() && !this.isBaby() && --this.timeUntilNextFeather <= 0) {
            this.spawnAtLocation(AMItemRegistry.ROADRUNNER_FEATHER.get());
            this.timeUntilNextFeather = this.random.nextInt(24000) + 24000;
        }
        this.wingRotDelta = (float) ((double) this.wingRotDelta * 0.9D);
        Vec3 vector3d = this.getDeltaMovement();
        if (!this.onGround() && vector3d.y < 0.0D) {
            this.setDeltaMovement(vector3d.multiply(1.0D, 0.8D, 1.0D));
        }
        this.wingRotation += this.wingRotDelta * 2.0F;

        if(this.entityData.get(ATTACK_TICK) > 0){
            if(this.entityData.get(ATTACK_TICK) == 2 && this.getTarget() != null && this.distanceTo(this.getTarget()) < 1.3D){
                this.getTarget().hurt(this.damageSources().mobAttack(this), 2);
            }
            this.entityData.set(ATTACK_TICK, this.entityData.get(ATTACK_TICK) - 1);
            if(attackProgress < 5F){
                attackProgress++;
            }
        }else{
            if(attackProgress > 0F){
                attackProgress--;
            }
        }
    }

    public void tick(){
        super.tick();

        if (isMeep()) {
            if (!hasMeepSpeed) {
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(1F);
                hasMeepSpeed = true;
            }
        } else {
            if (hasMeepSpeed) {
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.45F);
                hasMeepSpeed = false;
            }
        }

        if (this.level().isClientSide && this.isMeep() && this.onGround() && !this.isInWaterOrBubble() && this.getDeltaMovement().lengthSqr() > 0.03D) {
            Vec3 vector3d = this.getViewVector(0.0F);
            final float yRotRad = this.getYRot() * Mth.DEG_TO_RAD;
            float f = Mth.cos(yRotRad) * 0.2F;
            float f1 = Mth.sin(yRotRad) * 0.2F;
            float f2 = 1.2F - this.random.nextFloat() * 0.7F;
            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX() - vector3d.x * (double) f2 + (double) f, this.getY() + random.nextFloat() * 0.2F, this.getZ() - vector3d.z * (double) f2 + (double) f1, 0.0D, 0.0D, 0.0D);
                this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX() - vector3d.x * (double) f2 - (double) f, this.getY() + random.nextFloat() * 0.2F, this.getZ() - vector3d.z * (double) f2 - (double) f1, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    public boolean causeFallDamage(float distance, float damageMultiplier) {
        return false;
    }

    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        if(!this.isMeep()){
            this.playSound(SoundEvents.CHICKEN_STEP, 0.15F, 1.0F);
        }
    }

    public boolean isFood(ItemStack stack) {
        return stack.is(AMTagRegistry.ROADRUNNER_BREEDABLES);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel p_241840_1_, AgeableMob p_241840_2_) {
        return AMEntityRegistry.ROADRUNNER.get().create(p_241840_1_);
    }

    public static boolean canRoadrunnerSpawn(EntityType<? extends Animal> animal, LevelAccessor worldIn, MobSpawnType reason, BlockPos pos, RandomSource random) {
        boolean spawnBlock = worldIn.getBlockState(pos.below()).is(AMTagRegistry.ROADRUNNER_SPAWNS);
        return spawnBlock && worldIn.getRawBrightness(pos, 0) > 8;
    }

    public boolean isMeep(){
        String s = ChatFormatting.stripFormatting(this.getName().getString());
        return (s != null && s.toLowerCase().contains("meep")) || AlexsMobs.isAprilFools();
    }
}
