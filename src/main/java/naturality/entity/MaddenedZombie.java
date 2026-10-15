package naturality.entity;

import naturality.Naturality;
import naturality.NaturalityEffects;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class MaddenedZombie extends Zombie implements MaddenedMob {
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(MaddenedZombie.class, EntityDataSerializers.BOOLEAN);
    public static final int RAGE_DURATION = 200;
    public static final int INSANITY_DURATION = 400;
    private static final AttributeModifier RAGE = new AttributeModifier(Naturality.id("maddened_zombie_rage"),
        1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private int rageTicks;

    public MaddenedZombie(EntityType<? extends Zombie> type, Level level) { super(type, level); }
    public int rageTicks() { return rageTicks; }
    public boolean isEnraged() { return getEntityData().get(ENRAGED); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ENRAGED, false);
    }
    @Override protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new RageLeapGoal(this));
    }
    public static final class RageLeapGoal extends LeapAtTargetGoal {
        private final MaddenedZombie zombie;
        public RageLeapGoal(MaddenedZombie zombie) { super(zombie, .4F); this.zombie = zombie; }
        @Override public boolean canUse() {
            var target = zombie.getTarget();
            return zombie.isEnraged() && !zombie.isNoAi() && !zombie.isPassenger() && !zombie.isInWater()
                && target != null && target.isAlive() && zombie.getSensing().hasLineOfSight(target) && super.canUse();
        }
        @Override public boolean canContinueToUse() { return zombie.isEnraged() && super.canContinueToUse(); }
        @Override public void start() { super.start(); zombie.needsSync = true; }
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean damaged = super.hurtServer(level, source, damage);
        if (damaged && damage > 0 && isAlive()) {
            rageTicks = RAGE_DURATION;
            updateRage();
        }
        return damaged;
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && living.isAlive())
            living.addEffect(new MobEffectInstance(NaturalityEffects.INSANITY, INSANITY_DURATION), this);
        return hit;
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide() && rageTicks > 0 && --rageTicks == 0) updateRage();
    }
    private void updateRage() {
        getEntityData().set(ENRAGED, rageTicks > 0);
        rageAttribute(Attributes.MOVEMENT_SPEED);
        rageAttribute(Attributes.ATTACK_DAMAGE);
    }
    private void rageAttribute(Holder<Attribute> attribute) {
        var instance = getAttribute(attribute);
        if (instance == null) return;
        if (rageTicks > 0) {
            if (!instance.hasModifier(RAGE.id())) instance.addTransientModifier(RAGE);
        } else instance.removeModifier(RAGE.id());
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("naturality:rage_ticks", rageTicks);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        rageTicks = Math.clamp(input.getIntOr("naturality:rage_ticks", 0), 0, RAGE_DURATION);
        updateRage();
    }
}
