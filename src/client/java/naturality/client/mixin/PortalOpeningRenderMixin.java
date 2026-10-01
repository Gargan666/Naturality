package naturality.client.mixin;
import naturality.client.portal.PortalOpeningClient;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class PortalOpeningRenderMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void naturality$prepareGlowMasks(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
        naturality.client.portal.PortalCrossingClient.beginGlowFrame(camera, partialTick);
    }
    @Inject(method = "extract", at = @At("TAIL"))
    private void naturality$renderOpening(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
        PortalOpeningClient.extract(state, frustum, camera, partialTick);
        naturality.client.fluid.WaterIntersection.extract(state, frustum, camera, partialTick);
        naturality.client.fluid.LavaIntersection.extract(state, frustum, camera, partialTick);
        naturality.client.portal.end.EndEyeGlow.extract(state, frustum, camera, partialTick);
        naturality.client.portal.PortalCrossingClient.extract(state, frustum, camera, partialTick);
    }
}


