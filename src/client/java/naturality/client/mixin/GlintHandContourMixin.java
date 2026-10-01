package naturality.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import naturality.client.glint.GlintContours;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GlintHandContourMixin {
    @SuppressWarnings("null") @Shadow @Final private RenderTarget hud3DTarget;

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void naturality$beginHandContours(CallbackInfo ci) {
        GlintContours.beginHandFrame();
    }

    // Replay while the exact hand model-view transform and hand depth are still active.
    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE",
        target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private void naturality$renderHandContours(CameraRenderState camera, PlayerRenderState player,
            GpuTextureView depthView, CallbackInfo ci) {
        var color = ((GameRenderer) (Object) this).mainRenderTarget();
        var depth = depthView == hud3DTarget.getDepthTextureView() ? hud3DTarget : color;
        GlintContours.renderPreview(color.getColorTexture(), color.getColorTextureView(),
            depth.getDepthTexture(), depthView);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void naturality$endHandContours(CallbackInfo ci) {
        GlintContours.endFrame();
    }
}
