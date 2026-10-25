package naturality.client.rendering.fog.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.client.rendering.fog.EndFogComposite.EndFogTransparency;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import naturality.client.rendering.fog.EndFogComposite;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.google.common.collect.ImmutableMap;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class FogMixins {
    private FogMixins() {}

    @Mixin(AtmosphericFogEnvironment.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DirectionalAtmosphereFogMixin {
        // The sky already draws its sunset in world space. Tinting the entire clear
        // color by camera forwards makes that sky (and its fog capture) change on yaw.
        @ModifyExpressionValue(method = "getBaseColor", at = @At(value = "INVOKE",
            target = "Lorg/joml/Vector3fc;dot(FFF)F"))
        private float naturality$stableHorizon(float facing, ClientLevel level, Camera camera,
                int renderDistance, float partialTicks) {
            return level.dimension().equals(Level.OVERWORLD) && EndFogTransparency.isActive(level, camera)
                ? 0.0F : facing;
        }
    }

    @Mixin(LevelRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndFogCompositeMixin {
        @SuppressWarnings("null") @Shadow @Final private LevelTargetBundle targets;
        @Unique private @org.jspecify.annotations.Nullable GpuBufferSlice naturality$fog;
        @Unique private @org.jspecify.annotations.Nullable CloudRenderer naturality$clouds;
        @Unique private @org.jspecify.annotations.Nullable CloudStatus naturality$cloudStatus;
        @Unique private @org.jspecify.annotations.Nullable ChunkSectionsToRender naturality$waterChunks;
        @Unique private boolean naturality$replayTransparency;
        @Unique private boolean naturality$backgroundClouds;
        @SuppressWarnings("null") @Shadow @Final private CloudRenderer cloudRenderer;
        @SuppressWarnings("null") @Shadow @Final private net.minecraft.client.renderer.state.OptionsRenderState optionsRenderState;
        @SuppressWarnings("null") @Shadow @Final private net.minecraft.client.renderer.state.level.LevelRenderState levelRenderState;
        @Shadow protected abstract void executeClassicTransparency(ChunkSectionsToRender chunks,
            FeatureRenderDispatcher.PreparedFrame features, RenderPass pass);

        @Inject(method = "render", at = @At("HEAD"))
        private void naturality$beginFrame(GraphicsResourceAllocator allocator, boolean outline,
                CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor,
                boolean sky, boolean consistentDepth, CallbackInfo ci) {
            naturality.client.api.rendering.ShaderPacks.prepareFrame();
            EndFogComposite.beginFrame(fogColor);
            naturality.client.systems.weather.wind.WindRendering.beginFrame();
            naturality.client.ui.menus.previews.MenuSurface.MenuFog.prepare();
            naturality.client.fluids.water.WaterComposite.beginFrame();
            naturality.client.sky.shared.clouds.CloudLayerRenderer.WaterOrder.beginFrame();
            naturality.client.fluids.water.WaterVisuals.prepareFrame();
            naturality$fog = fog;
            naturality$clouds = null;
            naturality$backgroundClouds = false;
            naturality$waterChunks = null;
        }

        @Inject(method = "executeClassicTransparency", at = @At("HEAD"), cancellable = true)
        private void naturality$holdWaterTransparency(ChunkSectionsToRender chunks,
                FeatureRenderDispatcher.PreparedFrame features, RenderPass pass, CallbackInfo ci) {
            if (!naturality$replayTransparency && naturality.client.fluids.water.WaterVisuals.ready()) {
                // Close the opaque pass before sampling its color/depth for optics.
                naturality$waterChunks = chunks;
                ci.cancel();
                return;
            }
            naturality$renderBackgroundClouds(pass);
        }

        @Inject(method = "executeOit", at = @At("HEAD"))
        private void naturality$waterBeforeOit(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features, CallbackInfo ci) {
            if (naturality.client.api.rendering.ShaderPacks.active()) return;
            naturality.client.fluids.water.WaterComposite.render(targets.main.get(), chunks, features, true);
            var target = targets.main.get();
            var color = target.getColorTextureView();
            if (color == null || !naturality$needsBackgroundClouds()) return;
            naturality.client.sky.shared.clouds.CloudLayerRenderer.WaterOrder.prepare(target,cloudRenderer,optionsRenderState.cloudStatus);
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Naturality clouds before scene transparency", color, Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty())) {
                naturality$renderBackgroundClouds(pass);
            }
        }

        @Unique private boolean naturality$needsBackgroundClouds() {
            if (naturality.client.api.rendering.ShaderPacks.active()) return false;
            // Menu cutoff covers also occur in translucent terrain. Finish those
            // before blending clouds, using the menu's cover-aware depth capture.
            if (naturality.client.ui.menus.previews.MenuPreview.active()) return false;
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return !naturality$backgroundClouds && level != null
                && level.dimension().equals(Level.OVERWORLD) && optionsRenderState.cloudStatus != CloudStatus.OFF
                && net.minecraft.util.ARGB.alpha(levelRenderState.cloudColor) > 0;
        }

        @Unique private void naturality$renderBackgroundClouds(RenderPass pass) {
            if (!naturality$needsBackgroundClouds()) return;
            RenderSystem.bindDefaultUniforms(pass);
            // Test against opaque scene depth before partially transparent glass,
            // water and particles write depth. Their later colors blend over clouds.
            naturality.client.sky.shared.clouds.CloudLayerRenderer.WaterOrder.render(cloudRenderer,optionsRenderState.cloudStatus,pass,false);
            naturality$backgroundClouds = true;
        }
        @Inject(method = "addMainPass", at = @At("HEAD"))
        private void naturality$captureBackground(CallbackInfo ci, @Local(argsOnly = true) FrameGraphBuilder frame) {
            if (naturality.client.api.rendering.ShaderPacks.active()) return;
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
            if (naturality$backgroundClouds) return;
            if (!EndFogComposite.ready()) { original.call(clouds, status, pass); return; }
            naturality$clouds = clouds;
            naturality$cloudStatus = status;
        }

        @WrapOperation(method = "executeOit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/CloudRenderer;renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"))
        private void naturality$deferOitClouds(CloudRenderer clouds, CloudStatus status, OitStage stage,
                GpuTextureView depth, OitRenderPassProvider.Parameters params, Operation<Void> original) {
            if (naturality$backgroundClouds) return;
            if (!EndFogComposite.ready()) { original.call(clouds, status, stage, depth, params); return; }
            naturality$clouds = clouds;
            naturality$cloudStatus = status;
        }

        @Inject(method = "executeOutline", at = @At("HEAD"))
        private void naturality$fogThenClouds(FeatureRenderDispatcher.PreparedFrame features, CallbackInfo ci) {
            // Iris owns the scene-color targets and their resolve/composite order.
            // Native full-screen passes cannot sample Minecraft's main target here.
            if (naturality.client.api.rendering.ShaderPacks.active()) return;
            // The opaque/OIT pass has closed. Replay classic transparency after
            // refraction, then finish the existing atmosphere/cloud ordering.
            var target = targets.main.get();
            var colorView = target.getColorTextureView();
            if (colorView == null) return;
            var waterChunks = naturality$waterChunks;
            if (waterChunks != null) {
                naturality.client.fluids.water.WaterComposite.render(target, waterChunks, features, false);
                if(naturality$needsBackgroundClouds())
                    naturality.client.sky.shared.clouds.CloudLayerRenderer.WaterOrder.prepare(target,cloudRenderer,optionsRenderState.cloudStatus);
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
            naturality.client.rendering.heatwaves.HeatWaves.render(target);
            EndFogComposite.render(target, naturality$fog);
            naturality.client.sky.shared.aurora.AuroraRenderer.render(target);
            if(naturality$backgroundClouds) {
                try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Naturality clouds in front of water",colorView,Optional.empty(),
                        target.getDepthTextureView(),OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    naturality.client.sky.shared.clouds.CloudLayerRenderer.WaterOrder.render(
                        cloudRenderer,optionsRenderState.cloudStatus,pass,true);
                }
            }
            var clouds = naturality$clouds;
            var cloudStatus = naturality$cloudStatus;
            if (clouds == null || cloudStatus == null) {
                naturality.client.fluids.water.WaterComposite.renderSurface(target);
                return;
            }
            // Clouds are a separate sorted overlay in either scene-transparency mode.
            // Keeping them out of the scene OIT coefficients prevents water fog from
            // changing cloud coverage or darkening the gradient on the final blend.
            boolean menu = naturality.client.ui.menus.previews.MenuPreview.active();
            if (menu) naturality.client.sky.shared.clouds.CloudLayerRenderer.WaterOrder.prepare(target, clouds, cloudStatus);
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Naturality clouds after edge fog", colorView, Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                if (menu) naturality.client.sky.shared.clouds.CloudLayerRenderer.WaterOrder.render(clouds, cloudStatus, pass, false);
                else clouds.render(cloudStatus, pass);
            }
            naturality$clouds = null;
            naturality.client.fluids.water.WaterComposite.renderSurface(target);
        }

        @Inject(method = "close", at = @At("TAIL"))
        private void naturality$close(CallbackInfo ci) {
            naturality.client.api.rendering.ShaderPacks.close();
            EndFogComposite.close();
            naturality.client.rendering.heatwaves.HeatWaves.close();
            naturality.client.sky.shared.aurora.AuroraRenderer.close();
            naturality.client.fluids.water.WaterComposite.close();
            naturality.client.sky.shared.clouds.CloudLayerRenderer.WaterOrder.close();
            naturality.client.fluids.water.WaterVisuals.close();
        }
    }

    @Mixin(FogRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndFogTransparencyMixin {
        @Unique private boolean naturality$transparentEnd;
        @Unique private float naturality$caveFog = 1.0F;

        @Inject(method = "setupFog", at = @At("RETURN"))
        private void naturality$selectEndMode(Camera camera, int distance, DeltaTracker delta,
                float darken, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
            naturality$transparentEnd = level.dimension().equals(net.minecraft.world.level.Level.END)
                && EndFogTransparency.isActive(level, camera);
            naturality$caveFog = naturality.client.rendering.lighting.hardcore.HardcoreDarkness.caveFog(level, camera);
            if (level.dimension().equals(net.minecraft.world.level.Level.END)
                    && camera.getFluidInCamera() == net.minecraft.world.level.material.FogType.NONE
                    && EndFogTransparency.isActive(level, camera)) {
                var fog = cir.getReturnValue();
                fog.color.set(0.115F, 0.065F, 0.175F, 1.0F);
                naturality.client.rendering.fog.EndFogComposite.EndFogTransparency.boundDistanceFog(fog);
            }
        }

        // Mark only the uploaded color. The actual FogData/sky clear color is untouched.
        @ModifyVariable(method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private Vector4f naturality$uploadMode(Vector4f color) {
            if (naturality.client.api.rendering.ShaderPacks.active()) return color;
            if (color.w == 0.0F) return color; // Preserve the renderer's empty/disabled fog buffer.
            // Magnitudes 2..3 carry world-only fog brightness. Sign still selects
            // the atmosphere composite. Never change FogData's sky/clear RGB.
            float alpha = naturality$caveFog < 1.0F ? 2.0F + naturality$caveFog : 1.0F;
            return new Vector4f(color.x, color.y, color.z, naturality$transparentEnd ? -alpha : alpha);
        }
    }

    /** Shared fog includes also compile in vertex shaders, where derivatives are unavailable. */
    @Mixin(ShaderManager.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PixelFogShaderMixin {
        @ModifyVariable(method = "loadShader", at = @At("STORE"), ordinal = 0)
        private static String naturality$fragmentFog(String source, Identifier location, Resource resource,
                ShaderType type, ImmutableMap.Builder<?, ?> output) {
            if (type != ShaderType.FRAGMENT) return source;
            int lineEnd = source.indexOf('\n');
            if (!source.startsWith("#version") || lineEnd < 0) return source;
            return source.substring(0, lineEnd + 1) + "#define NATURALITY_FRAGMENT_FOG\n" + source.substring(lineEnd + 1);
        }
    }
}
