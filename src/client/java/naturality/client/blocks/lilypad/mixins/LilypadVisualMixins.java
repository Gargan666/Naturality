package naturality.client.blocks.lilypad.mixins;

import naturality.client.blocks.lilypad.LilypadVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class LilypadVisualMixins {
    private LilypadVisualMixins() {}
    @Mixin(ParticleEngine.class)
    public static abstract class LilypadMotionRenderMixin {
        @Inject(method = "extract", at = @At("TAIL"))
        private void naturality$pads(ParticlesRenderState state, Frustum frustum, Camera camera, float partial, CallbackInfo ci) {
            LilypadVisuals.extract(state,frustum,camera,partial);
        }
    }
}
