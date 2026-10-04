package naturality.mixin;

import naturality.Naturality;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Share the vanilla AI damage stimulus, never damage or recursively call hurt. */
@Mixin(LivingEntity.class)
public abstract class CrowdPanicMixin {
    @Shadow private DamageSource lastDamageSource;
    @Shadow private long lastDamageStamp;

    @Unique private static final TagKey<EntityType<?>> naturality$CROWD_PANIC =
        TagKey.create(Registries.ENTITY_TYPE, Naturality.id("crowd_panic"));

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void naturality$alertCrowd(ServerLevel level, DamageSource source, float damage,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || damage <= 0 || !(source.getEntity() instanceof LivingEntity)) return;
        if (!((Object)this instanceof Mob struck) || !struck.is(naturality$CROWD_PANIC)) return;
        for (Mob neighbor : level.getEntitiesOfClass(Mob.class, struck.getBoundingBox().inflate(16),
                mob -> mob != struck && mob.isAlive() && !mob.isNoAi()
                    && mob.getType() == struck.getType() && mob.distanceToSqr(struck) <= 256)) {
            CrowdPanicMixin state = (CrowdPanicMixin)(Object)neighbor;
            state.lastDamageSource = source;
            state.lastDamageStamp = level.getGameTime();
        }
    }
}
