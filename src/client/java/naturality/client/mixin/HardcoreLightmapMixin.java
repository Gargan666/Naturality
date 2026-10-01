package naturality.client.mixin;

import naturality.client.lighting.HardcoreDarkness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class HardcoreLightmapMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void naturality$darkness(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (!state.needsUpdate || level == null) return;
        state.ambientColor = new Vector3f(state.ambientColor).mul(HardcoreDarkness.ambientMultiplier(level));
        state.skyFactor *= HardcoreDarkness.atmosphere(level, client.gameRenderer.mainCamera(), partialTicks);
    }
}
