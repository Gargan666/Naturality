package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import naturality.client.sky.SunBeams;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SunBeamsMixin {
    @SuppressWarnings("null") @Shadow @Final private TextureAtlas celestialsAtlas;
    @Unique private @org.jspecify.annotations.Nullable SunBeams naturality$beams;
    @Unique private float naturality$sunAngle;

    @Inject(method = "render", at = @At("HEAD"))
    private void naturality$angle(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
        naturality$sunAngle = state.sunAngle;
    }

    @Inject(method = "renderSun", at = @At("HEAD"))
    private void naturality$beams(RenderPass pass, float rainBrightness, PoseStack pose, CallbackInfo ci) {
        if (!naturality.config.NaturalityConfig.get().effects.sunBeams) return;
        var effect = naturality$beams;
        if (effect == null) { effect = new SunBeams(); naturality$beams = effect; }
        effect.render(pass, pose, naturality$sunAngle, rainBrightness,
            celestialsAtlas.getSprite(Identifier.withDefaultNamespace("sun")));
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void naturality$close(CallbackInfo ci) {
        if (naturality$beams != null) {
            naturality$beams.close();
            naturality$beams = null;
        }
    }
}
