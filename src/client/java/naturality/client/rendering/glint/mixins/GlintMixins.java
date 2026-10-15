package naturality.client.rendering.glint.mixins;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import naturality.client.rendering.glint.GlintContours;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.api.geometry.ModelCapture.FlatModelAlpha;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import naturality.client.rendering.glint.GlintContours.InventoryGlintContours;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class GlintMixins {
    private GlintMixins() {}

    @Mixin(LevelRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GlintContourRenderMixin {
        @SuppressWarnings("null") @Shadow @Final private LevelTargetBundle targets;
        @Unique private @org.jspecify.annotations.Nullable GpuBufferSlice naturality$contourFog;

        @Inject(method = "render", at = @At("HEAD"))
        private void naturality$begin(GraphicsResourceAllocator allocator, boolean outline,
                CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor,
                boolean sky, boolean consistentDepth, CallbackInfo ci) {
            GlintContours.beginFrame();
            naturality$contourFog = fog;
        }

        @Inject(method = "executeOutline", at = @At("TAIL"))
        private void naturality$render(CallbackInfo ci) {
            GlintContours.render(targets.main.get(), naturality$contourFog);
        }

        @Inject(method = "render", at = @At("RETURN"))
        private void naturality$end(CallbackInfo ci) { GlintContours.endFrame(); }

        @Inject(method = "close", at = @At("TAIL"))
        private void naturality$close(CallbackInfo ci) { GlintContours.close(); }
    }

    @Mixin(EquipmentLayerRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GlintEquipmentContourMixin {
        @WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
        private <S> void naturality$armor(OrderedSubmitNodeCollector collector, Model<? super S> model,
                S state, PoseStack pose, RenderType type, int light, int overlay, int color,
                @org.jspecify.annotations.Nullable UvMapping uv, int outline, Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
            original.call(collector, model, state, pose, type, light, overlay, color, uv, outline);
            if (!stack.hasFoil()) return;
            boolean procedural = type.pipeline() == RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT;
            // Base layers define the silhouette, including enchanted trimmed armor.
            if (!procedural && type.pipeline() != RenderPipelines.ARMOR_CUTOUT_NO_CULL) return;
            var buffer = GlintContours.armor(FlatModelAlpha.texture(type), procedural);
            if (buffer == null) return;
            model.setupAnim(state);
            var mapped = uv == null ? buffer : uv.wrap(buffer);
            if (mapped != null) model.renderToBuffer(pose, mapped, light, overlay, color);
        }
    }

    @Mixin(GameRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GlintHandContourMixin {
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

    @Mixin(ItemFeatureRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GlintItemContourMixin {
        @ModifyExpressionValue(method = "prepareMainSubmit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
        private VertexConsumer naturality$contour(VertexConsumer original,
                @Local(argsOnly = true) ItemFeatureRenderer.Submit submit, @Local RenderType renderType) {
            if ((submit.displayContext() != ItemDisplayContext.GROUND && submit.displayContext() != ItemDisplayContext.FIXED && submit.displayContext() != ItemDisplayContext.HEAD && submit.displayContext() != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && submit.displayContext() != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && submit.displayContext() != ItemDisplayContext.FIRST_PERSON_LEFT_HAND && submit.displayContext() != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) || submit.foilType() == ItemStackRenderState.FoilType.NONE) return original;
            return GlintContours.capture(original, FlatModelAlpha.texture(renderType), false);
        }
    }

    @Mixin(ItemStackRenderState.class)
    @org.jspecify.annotations.NullMarked
    public interface GlintItemLayersAccess {
        @Accessor("activeLayerCount") int naturality$activeLayers();
        @Accessor("layers") ItemStackRenderState.LayerRenderState[] naturality$layers();
    }

    @Mixin(ItemStackRenderState.LayerRenderState.class)
    @org.jspecify.annotations.NullMarked
    public interface GlintLayerFoilAccess {
        @Accessor("foilType") ItemStackRenderState.FoilType naturality$foil();
    }

    @Mixin(PictureInPictureRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GlintPreviewContourMixin {
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

    @Mixin(GuiRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class InventoryGlintContourMixin {
        @SuppressWarnings("null") @Shadow @Final private GuiRenderState renderState;
        @Inject(method = "submitBlitFromItemAtlas", at = @At("TAIL"))
        private void naturality$outline(GuiItemRenderState item, GuiItemAtlas.SlotView slot, CallbackInfo ci) {
            InventoryGlintContours.submit(renderState, item, slot);
        }
    }
}
