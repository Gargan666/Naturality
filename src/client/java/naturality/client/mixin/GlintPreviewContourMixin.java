package naturality.client.mixin;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import naturality.client.glint.GlintContours;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public abstract class GlintPreviewContourMixin {
    @SuppressWarnings("null") @Shadow private GpuTexture texture;
    @SuppressWarnings("null") @Shadow private GpuTextureView textureView;
    @SuppressWarnings("null") @Shadow private GpuTexture depthTexture;
    @SuppressWarnings("null") @Shadow private GpuTextureView depthTextureView;
    @Unique private boolean naturality$entityPreview;

    // The preview depth texture normally cannot be copied. Match RenderTarget's
    // sampled/copyable depth usage so the contour can respect preview occlusion.
    @ModifyArg(method = "prepareTexturesAndProjection", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createTexture(Ljava/util/function/Supplier;ILcom/mojang/renderpearl/api/GpuFormat;IIII)Lcom/mojang/renderpearl/api/textures/GpuTexture;", ordinal = 1), index = 1)
    private int naturality$copyableDepth(int usage) { return usage | 6; }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void naturality$begin(PictureInPictureRenderState state, GuiRenderState gui,
            FeatureRenderDispatcher features, int scale, CallbackInfo ci) {
        naturality$entityPreview = state instanceof GuiEntityRenderState;
        if (naturality$entityPreview) GlintContours.beginPreview(scale * state.scale());
    }

    @Inject(method = "prepare", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/pip/PictureInPictureRenderer;blitTexture(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;)V"))
    private void naturality$outline(CallbackInfo ci) {
        if (naturality$entityPreview)
            GlintContours.renderPreview(texture, textureView, depthTexture, depthTextureView);
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void naturality$finish(CallbackInfo ci) {
        if (naturality$entityPreview) GlintContours.endFrame();
        naturality$entityPreview = false;
    }
}
