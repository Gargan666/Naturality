package naturality.mixin;

import naturality.NaturalityEffects;
import naturality.weather.DistortionState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class DistortionEffectMixin implements DistortionState {
    @Unique private static final EntityDataAccessor<Float> naturality$DISTORTION =
        SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
    @Unique private float naturality$from, naturality$to;
    @Unique private long naturality$updateTime;
    @Unique private boolean naturality$received;
    @Inject(method="defineSynchedData", at=@At("TAIL"))
    private void naturality$define(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(naturality$DISTORTION, 0F);
    }
    @Inject(method="tick", at=@At("HEAD"))
    private void naturality$turn(CallbackInfo ci) {
        var entity = (LivingEntity)(Object)this;
        if (entity.level().isClientSide()) return;
        float current = entity.getEntityData().get(naturality$DISTORTION);
        float target = entity.hasEffect(NaturalityEffects.DISTORTION) ? 1 : 0;
        entity.getEntityData().set(naturality$DISTORTION,
            current + Math.clamp(target-current, -1F/80, 1F/80));
    }
    @Inject(method="onSyncedDataUpdated", at=@At("HEAD"))
    private void naturality$receive(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        if (!accessor.equals(naturality$DISTORTION)) return;
        float t = Math.clamp((System.nanoTime()-naturality$updateTime)/50_000_000F, 0, 1);
        float from = naturality$from+(naturality$to-naturality$from)*t;
        naturality$from = naturality$received ? from : ((LivingEntity)(Object)this).getEntityData().get(naturality$DISTORTION);
        naturality$to = ((LivingEntity)(Object)this).getEntityData().get(naturality$DISTORTION);
        naturality$received = true;
        naturality$updateTime = System.nanoTime();
    }
    public float naturality$distortion(boolean render) {
        var entity = (LivingEntity)(Object)this;
        float value = entity.getEntityData().get(naturality$DISTORTION);
        if (render && entity.level().isClientSide() && naturality$received) {
            float t = Math.clamp((System.nanoTime()-naturality$updateTime)/50_000_000F, 0, 1);
            value = naturality$from+(naturality$to-naturality$from)*t;
        }
        return value*value*(3-2*value);
    }
    @Inject(method="addAdditionalSaveData", at=@At("TAIL"))
    private void naturality$save(ValueOutput output, CallbackInfo ci) {
        output.putFloat("naturality:distortion_rotation", ((LivingEntity)(Object)this).getEntityData().get(naturality$DISTORTION));
    }
    @Inject(method="readAdditionalSaveData", at=@At("TAIL"))
    private void naturality$load(ValueInput input, CallbackInfo ci) {
        var entity = (LivingEntity)(Object)this;
        entity.getEntityData().set(naturality$DISTORTION, Math.clamp(input.getFloatOr("naturality:distortion_rotation",
            entity.hasEffect(NaturalityEffects.DISTORTION) ? 1F : 0F), 0, 1));
    }
}
