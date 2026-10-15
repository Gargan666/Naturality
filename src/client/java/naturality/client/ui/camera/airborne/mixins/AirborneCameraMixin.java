package naturality.client.ui.camera.airborne.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.client.ui.camera.airborne.AirborneTilt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
@org.jspecify.annotations.NullMarked
public abstract class AirborneCameraMixin implements naturality.client.ui.camera.airborne.AirborneTilt.AirborneCameraState {
    @Shadow private @org.jspecify.annotations.Nullable Entity entity;
    @Unique private final AirborneTilt naturality$tilt = new AirborneTilt();
    @Unique private @org.jspecify.annotations.Nullable Entity naturality$lastEntity;
    @Unique private @org.jspecify.annotations.Nullable Object naturality$lastLevel;

    @Unique private boolean naturality$eligible() {
        return entity instanceof LocalPlayer player && player == Minecraft.getInstance().player
            && Minecraft.getInstance().options.getCameraType().isFirstPerson()
            && !player.isSpectator() && !player.isFallFlying()
            && !player.hasPose(net.minecraft.world.entity.Pose.FALL_FLYING)
            && !player.isPassenger() && !player.isSleeping() && !player.isDeadOrDying()
            && !player.isInWater() && !player.isInLava() && !player.onClimbable();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void naturality$airborneTick(CallbackInfo ci) {
        var cameraEntity = entity;
        if (cameraEntity != naturality$lastEntity || cameraEntity != null && cameraEntity.level() != naturality$lastLevel) {
            naturality$tilt.reset();
            naturality$lastEntity = cameraEntity;
            naturality$lastLevel = cameraEntity == null ? null : cameraEntity.level();
        }
        if (!naturality$eligible() || !(cameraEntity instanceof LocalPlayer player)) { naturality$tilt.reset(); return; }
        if (player.level().tickRateManager().isEntityFrozen(player)) return;
        naturality$tilt.tick(player.onGround() && !player.getAbilities().flying, player.getDeltaMovement().y, player.getAbilities().flying);
    }

    @ModifyExpressionValue(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float naturality$airbornePitch(float pitch, float partialTicks) {
        if (!naturality$eligible()) { naturality$tilt.reset(); return pitch; }
        return net.minecraft.util.Mth.clamp(pitch + naturality$tilt.sample(partialTicks), -90, 90);
    }

    @Override public float naturality$fallMultiplier(float partialTick) {
        return naturality$eligible() ? Math.max(0, -naturality$tilt.sample(partialTick) / 2.5F) : 0;
    }
}
