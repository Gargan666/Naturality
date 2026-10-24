package naturality.effects.stunned;

import naturality.Naturality;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/** A short action lock that leaves look rotation, external impulses and gravity intact. */
@NullMarked
public final class Stunned extends MobEffect {
    public static final Holder<MobEffect> EFFECT = Registry.registerForHolder(
        BuiltInRegistries.MOB_EFFECT, Naturality.id("stunned"), new Stunned());

    public interface State {
        boolean naturality$isStunned();
        void naturality$setStunned(boolean stunned);
    }

    private Stunned() { super(MobEffectCategory.HARMFUL, 0xFFFF55); }

    public static void initialize() {
        // Includes contact attacks that do not call Mob.doHurtTarget (for example, slimes).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((_, source, _) -> !active(source.getDirectEntity()));
    }

    public static boolean active(@org.jspecify.annotations.Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        return living.level().isClientSide()
            ? ((State) living).naturality$isStunned() : living.hasEffect(EFFECT);
    }

    public static void apply(LivingEntity entity, LivingEntity source) {
        entity.addEffect(new MobEffectInstance(EFFECT, 20, 0, false, false, true), source);
    }

    public static void synchronize(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            ((State) entity).naturality$setStunned(entity.hasEffect(EFFECT));
        }
        if (active(entity)) suppressActions(entity);
    }

    @Override public void onEffectStarted(LivingEntity entity, int amplifier) {
        synchronize(entity);
    }

    public static void suppressActions(LivingEntity entity) {
        entity.stopUsingItem();
        entity.setSprinting(false);
        entity.setJumping(false);
        entity.xxa = 0;
        entity.yya = 0;
        entity.zza = 0;
        if (entity instanceof Player player && player.isFallFlying()) player.stopFallFlying();
        if (entity instanceof ServerPlayer player) player.setLastClientInput(Input.EMPTY);
        if (entity instanceof Mob mob && !entity.level().isClientSide()) clearAggro(mob);
    }

    public static void clearAggro(Mob mob) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        if (mob instanceof NeutralMob neutral) neutral.stopBeingAngry();
        var brain = mob.getBrain();
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    public static void stopAi(Mob mob, GoalSelector goals, GoalSelector targets) {
        goals.getAvailableGoals().forEach(goal -> { if (goal.isRunning()) goal.stop(); });
        targets.getAvailableGoals().forEach(goal -> { if (goal.isRunning()) goal.stop(); });
        suppressActions(mob);
        mob.getLookControl().tick();
    }
}
