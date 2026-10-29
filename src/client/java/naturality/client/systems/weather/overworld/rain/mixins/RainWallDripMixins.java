package naturality.client.systems.weather.overworld.rain.mixins;

import naturality.client.systems.weather.overworld.rain.RainWallDrips;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Integration for the rectangular wall-clipped particle state. */
public final class RainWallDripMixins {
    private RainWallDripMixins() {}

    @Mixin(ParticleEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WallParticleExtractionMixin {
        @Inject(method = "extract", at = @At("TAIL"))
        private void naturality$wallDrips(ParticlesRenderState output, Frustum frustum, Camera camera,
                float partial, CallbackInfo ci) {
            RainWallDrips.extract(output, frustum, camera, partial);
        }
    }
}
