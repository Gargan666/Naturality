package naturality.client.mixin;

import naturality.client.fog.EndFogTransparency;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class EndFogTransparencyMixin {
    @Unique private boolean naturality$transparentEnd;
    @Unique private float naturality$caveFog = 1.0F;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void naturality$selectEndMode(Camera camera, int distance, DeltaTracker delta,
            float darken, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        naturality$transparentEnd = level.dimension().equals(net.minecraft.world.level.Level.END)
            && EndFogTransparency.isActive(level, camera);
        naturality$caveFog = naturality.client.lighting.HardcoreDarkness.caveFog(level, camera);
        if (level.dimension().equals(net.minecraft.world.level.Level.END)
                && camera.getFluidInCamera() == net.minecraft.world.level.material.FogType.NONE
                && EndFogTransparency.isActive(level, camera)) {
            cir.getReturnValue().color.set(0.115F, 0.065F, 0.175F, 1.0F);
        }
    }

    // Mark only the uploaded color. The actual FogData/sky clear color is untouched.
    @ModifyVariable(method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vector4f naturality$uploadMode(Vector4f color) {
        if (color.w == 0.0F) return color; // Preserve the renderer's empty/disabled fog buffer.
        // Magnitudes 2..3 carry world-only fog brightness. Sign still selects
        // the atmosphere composite. Never change FogData's sky/clear RGB.
        float alpha = naturality$caveFog < 1.0F ? 2.0F + naturality$caveFog : 1.0F;
        return new Vector4f(color.x, color.y, color.z, naturality$transparentEnd ? -alpha : alpha);
    }
}
