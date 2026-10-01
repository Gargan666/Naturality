package naturality.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import naturality.client.fog.EndFogComposite;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class OverworldFogAtmosphereMixin {
    @SuppressWarnings("null") @Shadow @Final private RenderTarget renderTarget;
    @Invoker("renderSkyDisc") protected abstract void naturality$skyDisc(RenderPass pass, Vector3fc color);
    @Invoker("renderSunriseAndSunset") protected abstract void naturality$sunset(RenderPass pass, PoseStack pose, float angle, Vector4fc color);
    @Invoker("renderDarkDisc") protected abstract void naturality$darkDisc(RenderPass pass);

    // RETURN is after vanilla closes its render pass. Never copy or render into a
    // different target while the original sky pass is still open.
    @Inject(method = "render", at = @At("RETURN"))
    private void naturality$atmosphere(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
        EndFogComposite.captureAtmosphere(renderTarget, pass -> {
            naturality$skyDisc(pass, state.skyColor);
            naturality$sunset(pass, new PoseStack(), state.sunAngle, state.sunriseAndSunsetColor);
            if (state.shouldRenderDarkDisc) naturality$darkDisc(pass);
        });
    }
}
