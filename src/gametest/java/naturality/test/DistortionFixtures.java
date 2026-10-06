package naturality.test;

import naturality.NaturalityEffects;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Immediate inversion only for isolated geometry fixtures; live tests use effect commands and ticks. */
public final class DistortionFixtures {
    private DistortionFixtures() {}
    @SuppressWarnings("unchecked")
    public static void invert(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(NaturalityEffects.DISTORTION, 12000));
        try {
            for (var field : LivingEntity.class.getDeclaredFields()) {
                if (field.getName().contains("DISTORTION") && field.getType()==EntityDataAccessor.class) {
                    field.setAccessible(true);
                    entity.getEntityData().set((EntityDataAccessor<Float>)field.get(null), 1F);
                    return;
                }
            }
            throw new AssertionError("Missing per-entity distortion data");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
