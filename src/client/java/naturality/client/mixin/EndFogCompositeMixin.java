package naturality.client.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import naturality.client.fog.EndFogComposite;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import java.util.Optional;
import java.util.OptionalDouble;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class EndFogCompositeMixin {
    @SuppressWarnings("null") @Shadow @Final private LevelTargetBundle targets;
    @Unique private @org.jspecify.annotations.Nullable GpuBufferSlice naturality$fog;
    @Unique private @org.jspecify.annotations.Nullable CloudRenderer naturality$clouds;
    @Unique private @org.jspecify.annotations.Nullable CloudStatus naturality$cloudStatus;
    @Unique private @org.jspecify.annotations.Nullable ChunkSectionsToRender naturality$waterChunks;
    @Unique private boolean naturality$replayTransparency;
    @Shadow protected abstract void executeClassicTransparency(ChunkSectionsToRender chunks,
        FeatureRenderDispatcher.PreparedFrame features, RenderPass pass);

    @Inject(method = "render", at = @At("HEAD"))
    private void naturality$beginFrame(GraphicsResourceAllocator allocator, boolean outline,
            CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor,
            boolean sky, boolean consistentDepth, CallbackInfo ci) {
        EndFogComposite.beginFrame(fogColor);
        naturality.client.fluid.WaterComposite.beginFrame();
        naturality.client.fluid.WaterVisuals.prepareFrame();
        naturality$fog = fog;
        naturality$clouds = null;
        naturality$waterChunks = null;
    }

    @Inject(method = "executeClassicTransparency", at = @At("HEAD"), cancellable = true)
    private void naturality$holdWaterTransparency(ChunkSectionsToRender chunks,
            FeatureRenderDispatcher.PreparedFrame features, RenderPass pass, CallbackInfo ci) {
        if (naturality$replayTransparency || !naturality.client.fluid.WaterVisuals.ready()) return;
        // Vanilla shares its opaque pass with classic transparency. Let it close
        // before sampling opaque depth and color; replay transparency afterward.
        naturality$waterChunks = chunks;
        ci.cancel();
    }

    @Inject(method = "executeOit", at = @At("HEAD"))
    private void naturality$waterBeforeOit(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features, CallbackInfo ci) {
        naturality.client.fluid.WaterComposite.render(targets.main.get(), chunks);
    }

    @Inject(method = "addMainPass", at = @At("HEAD"))
    private void naturality$captureBackground(CallbackInfo ci, @Local(argsOnly = true) FrameGraphBuilder frame) {
        // Also runs when boss fog suppresses the sky pass: the cleared background
        // is then exactly what transparent geometry should reveal.
        var pass = frame.addPass("naturality_end_sky_capture");
        targets.main = pass.readsAndWrites(targets.main);
        var target = targets.main;
        pass.executes(() -> EndFogComposite.captureSky(target.get()));
    }

    @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/CloudRenderer;render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void naturality$deferClouds(CloudRenderer clouds, CloudStatus status, RenderPass pass, Operation<Void> original) {
        if (!EndFogComposite.ready()) { original.call(clouds, status, pass); return; }
        naturality$clouds = clouds;
        naturality$cloudStatus = status;
    }

    @WrapOperation(method = "executeOit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/CloudRenderer;renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"))
    private void naturality$deferOitClouds(CloudRenderer clouds, CloudStatus status, OitStage stage,
            GpuTextureView depth, OitRenderPassProvider.Parameters params, Operation<Void> original) {
        if (!EndFogComposite.ready()) { original.call(clouds, status, stage, depth, params); return; }
        naturality$clouds = clouds;
        naturality$cloudStatus = status;
    }

    @Inject(method = "executeOutline", at = @At("HEAD"))
    private void naturality$fogThenClouds(FeatureRenderDispatcher.PreparedFrame features, CallbackInfo ci) {
        // The opaque/OIT pass has closed. Replay classic transparency after
        // refraction, then finish the existing atmosphere/cloud ordering.
        var target = targets.main.get();
        var colorView = target.getColorTextureView();
        if (colorView == null) return;
        var waterChunks = naturality$waterChunks;
        if (waterChunks != null) {
            naturality.client.fluid.WaterComposite.render(target, waterChunks);
            naturality$replayTransparency = true;
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Naturality transparency after underwater optics", colorView, Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                executeClassicTransparency(waterChunks, features, pass);
            } finally {
                naturality$replayTransparency = false;
                naturality$waterChunks = null;
            }
        }
        EndFogComposite.render(target, naturality$fog);
        naturality.client.sky.AuroraRenderer.render(target);
        var clouds = naturality$clouds;
        var cloudStatus = naturality$cloudStatus;
        if (clouds == null || cloudStatus == null) {
            return;
        }
        // Clouds are a separate sorted overlay in either scene-transparency mode.
        // Keeping them out of the scene OIT coefficients prevents water fog from
        // changing cloud coverage or darkening the gradient on the final blend.
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality clouds after edge fog", colorView, Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            clouds.render(cloudStatus, pass);
        }
        naturality$clouds = null;
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void naturality$close(CallbackInfo ci) {
        EndFogComposite.close();
        naturality.client.sky.AuroraRenderer.close();
        naturality.client.fluid.WaterComposite.close();
        naturality.client.fluid.WaterVisuals.close();
    }
}
