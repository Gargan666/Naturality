package naturality.client.api.rendering.mixins;

import java.util.Map;
import naturality.client.fluids.shared.ProceduralFluids;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.google.common.collect.ImmutableMap;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import naturality.client.config.shaders.FeatureShaderSettings.LightingShaderSettings;
import naturality.client.config.shaders.FeatureShaderSettings.FogShaderSettings;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.Resource;
import com.mojang.blaze3d.pipeline.PipelineCache;
import java.util.concurrent.Executor;
import naturality.client.api.rendering.ShaderPreparation;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Final;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class RenderingMixins {
    private RenderingMixins() {}

    @Mixin(TextureAtlas.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FluidAtlasMixin {
        @SuppressWarnings("null") @Shadow private Map<Identifier, TextureAtlasSprite> texturesByName;

        @Inject(method = "upload", at = @At("HEAD"))
        private void naturality$retireSnowSprites(SpriteLoader.Preparations preparations, CallbackInfo ci) {
            naturality.client.blocks.snow.SnowTextureAtlas.retire((TextureAtlas)(Object)this);
        }

        @Inject(method = "upload", at = @At("TAIL"))
        private void naturality$fluidBounds(SpriteLoader.Preparations preparations, CallbackInfo ci) {
            if (((TextureAtlas) (Object) this).location().equals(naturality.client.api.rendering.AtlasLocations.BLOCKS)) {
                naturality.client.rendering.breaking.BreakingTextures.reset();
                naturality.client.rendering.distantdetail.DistantTextures.loaded(texturesByName);
                naturality.client.rendering.shadows.BlockGridShadow.reset();
                naturality.client.api.geometry.ModelCapture.FlatModelAlpha.clear();
                naturality.client.api.rendering.layers.SurfaceLayerModel.SnowOverlayModel.atlasLoaded(texturesByName);
                naturality.client.blocks.snow.SnowTextureAtlas.loaded((TextureAtlas)(Object)this,texturesByName);
                ProceduralFluids.atlasLoaded(texturesByName);
                naturality.client.blocks.glass.GlassShine.loaded(texturesByName);
                naturality.client.blocks.fire.ProceduralFire.atlasLoaded(texturesByName);
            }
        }
    }

    @Mixin(RenderPipelines.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FluidPipelineMixin {
        @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withLocation(Ljava/lang/String;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
        private static RenderPipeline.Builder naturality$opaqueWaterShading(RenderPipeline.Builder builder, String location) {
            // Explicit opt-in: never affect cutout, translucent, mask or optional renderer pipelines.
            if (location.equals("pipeline/solid_terrain") || location.equals("pipeline/solid_terrain_multidraw"))
                builder.withShaderDefine("NATURALITY_WATER_OPAQUE_TERRAIN");
            return builder.withLocation(location);
        }

        @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withFragmentShader(Ljava/lang/String;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
        private static RenderPipeline.Builder naturality$fluidLayout(RenderPipeline.Builder builder, String shader) {
            if (shader.equals("core/terrain")) {
                builder.withBindGroupLayout(naturality.client.ui.menus.previews.MenuSurface.MenuFog.LAYOUT);
                builder.withBindGroupLayout(naturality.client.systems.weather.wind.WindRendering.LAYOUT);
                builder.withBindGroupLayout(ProceduralFluids.LAYOUT);
                builder.withBindGroupLayout(naturality.client.blocks.glass.GlassShine.LAYOUT);
                // Glass visibility needs the same lightmap in every OIT stage.
                builder.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER2);
                builder.withBindGroupLayout(naturality.client.blocks.fire.ProceduralFire.LAYOUT);
                builder.withBindGroupLayout(naturality.client.fluids.water.WaterVisuals.LAYOUT);
                builder.withBindGroupLayout(naturality.client.fluids.water.WaterVisuals.SCENE_LAYOUT);
            }
            return builder.withFragmentShader(shader);
        }
    }

    @Mixin(ChunkSectionsToRender.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FluidTerrainMixin {
        @Inject(method = "renderLayers", at = @At("HEAD"))
        private void naturality$bindFluids(ChunkSectionLayer[] layers, GpuSampler sampler, RenderPass pass,
                GpuTextureView atlas, GpuTextureView lightmap, RenderPipeline override,
                RenderPipeline multidrawOverride, CallbackInfo ci) {
            ProceduralFluids.bind(pass);
            naturality.client.blocks.glass.GlassShine.bind(pass);
            naturality.client.ui.menus.previews.MenuSurface.MenuFog.bind(pass);
            naturality.client.systems.weather.wind.WindRendering.bind(pass);
            naturality.client.blocks.fire.ProceduralFire.bind(pass);
            naturality.client.fluids.water.WaterVisuals.bind(pass);
            naturality.client.fluids.water.WaterVisuals.bindScene(pass);
        }
    }

    /** Fabric redirects chunk tessellation away from vanilla ModelBlockRenderer. */
    @Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
    @org.jspecify.annotations.NullMarked
    public static abstract class IndigoSubmergedTerrainMixin {
        @SuppressWarnings("null") @Shadow private BlockAndTintGetter level;
        @SuppressWarnings("null") @Shadow private BlockPos pos;
        @SuppressWarnings("null") @Shadow private BlockState blockState;

        @Inject(method = "shouldCullFace", at = @At("HEAD"), cancellable = true)
        private void naturality$displacedSnowFace(net.minecraft.core.@org.jspecify.annotations.Nullable Direction direction, CallbackInfoReturnable<Boolean> cir) {
            if (direction == null || !naturality.config.gameplay.GameplaySettings.clientSnowWrapping()) return;
            var neighbor = pos.relative(direction);
            if (level.getBlockState(neighbor).is(net.minecraft.world.level.block.Blocks.SNOW)
                    && !naturality.blocks.snow.SnowGeometry.usesVanillaGeometry(level, neighbor)) {
                // State-only occlusion describes snow in its saved cell, not its
                // fitted surfaces. Keep the support face; actual snow depth-occludes it.
                cir.setReturnValue(false);
            }
        }

        @Inject(method = "transform", at = @At(value = "INVOKE",
            target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AltModelBlockRendererImpl;tintQuad(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MutableQuadView;)V",
            shift = At.Shift.AFTER))
        private void naturality$tagWater(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
            if (!(level instanceof RenderSectionRegion)) return;
            var modelOffset = blockState.getOffset(pos);
            int windTag = naturality.client.systems.weather.wind.WindRendering.tag(blockState);
            if (windTag != 255) {
                for (int i = 0; i < 4; i++) {
                    int color = quad.color(i);
                    int tag = naturality.client.systems.weather.wind.WindRendering.vertexTag(level, pos, blockState, quad.x(i)+(float)modelOffset.x, quad.y(i)+(float)modelOffset.y, quad.z(i)+(float)modelOffset.z);
                    if ((color >>> 24) == 255) quad.color(i, (color & 0xFFFFFF) | (tag << 24));
                }
            }
            // Still block-local here: translation into the section happens next.
            var fluidPos = blockState.getFluidState().is(FluidTags.WATER) ? pos : pos.relative(quad.lightFace());
            var fluid = level.getFluidState(fluidPos);
            if (!fluid.is(FluidTags.WATER)) return;
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                float y = pos.getY() + quad.y(i);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            int tag = naturality.client.fluids.shared.FluidGeometry.SubmergedFace.tag(minY, maxY, fluidPos.getY(), fluid.getHeight(level, fluidPos));
            if (tag == 255) return;
            for (int i = 0; i < 4; i++) {
                int color = quad.color(i);
                int alpha = color >>> 24;
                if (alpha == 255 || alpha >= 64 && alpha <= 117 || alpha >= 118 && alpha <= 192 || alpha >= 201 && alpha <= 240) quad.color(i, (color & 0xFFFFFF) | (tag << 24));
            }
        }
    }

    @Mixin(ShaderManager.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LightingShaderSettingsMixin {
        @Inject(method = "loadInclude", at = @At("HEAD"), cancellable = true)
        private static void naturality$lightingSettings(Identifier location, Resource resource,
                ImmutableMap.Builder<Identifier, ShaderSource.CachedIncludeSource> output, CallbackInfo ci) {
            String name;
            String source;
            if (location.equals(Identifier.parse("naturality:shaders/include/pixel_lighting_settings.glsl"))) {
                name = "pixel_lighting_settings.glsl";
                source = LightingShaderSettings.source();
            } else if (location.equals(Identifier.parse("naturality:shaders/include/pixel_fog_settings.glsl"))) {
                name = "pixel_fog_settings.glsl";
                source = FogShaderSettings.source();
            } else if (location.equals(Identifier.parse("naturality:shaders/include/feature_settings.glsl"))) {
                name = "feature_settings.glsl";
                source = naturality.client.config.shaders.FeatureShaderSettings.source();
            } else return;
            var id = Identifier.parse("naturality:" + name);
            output.put(id, ShaderSource.CachedIncludeSource.create(id, source));
            ci.cancel();
        }
    }

    @Mixin(PipelineCache.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PipelinePreparationMixin {
        @ModifyArg(method = "get", at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/device/GpuDevice;compilePipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 2)
        private Executor naturality$independentShaderPreparation(Executor terrainExecutor) {
            return ShaderPreparation.executor();
        }
    }

    /** Carry face-local water occupancy in the otherwise opaque terrain tint alpha. */
    @Mixin(ModelBlockRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SubmergedTerrainMixin {
        @SuppressWarnings("null") @Shadow @Final private QuadInstance quadInstance;

        @Inject(method = "putQuadWithTint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
        private void naturality$tagWater(BlockQuadOutput output, float x, float y, float z,
                BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
            if (!(level instanceof RenderSectionRegion)) return;
            var modelOffset = state.getOffset(pos);
            int windTag = naturality.client.systems.weather.wind.WindRendering.tag(state);
            if (windTag != 255) {
                for (int i = 0; i < 4; i++) {
                    int color = quadInstance.getColor(i);
                    int tag = naturality.client.systems.weather.wind.WindRendering.vertexTag(level, pos, state, quad.position(i).x()+(float)modelOffset.x, quad.position(i).y()+(float)modelOffset.y, quad.position(i).z()+(float)modelOffset.z);
                    if ((color >>> 24) == 255) quadInstance.setColor(i, (color & 0xFFFFFF) | (tag << 24));
                }
            }
            var fluidPos = state.getFluidState().is(FluidTags.WATER) ? pos : pos.relative(quad.direction());
            var fluid = level.getFluidState(fluidPos);
            if (!fluid.is(FluidTags.WATER)) return;
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                float yPos = pos.getY() + quad.position(i).y();
                minY = Math.min(minY, yPos); maxY = Math.max(maxY, yPos);
            }
            int tag = naturality.client.fluids.shared.FluidGeometry.SubmergedFace.tag(minY, maxY, fluidPos.getY(), fluid.getHeight(level, fluidPos));
            if (tag == 255) return;
            for (int i = 0; i < 4; i++) {
                int color = quadInstance.getColor(i);
                int alpha = color >>> 24;
                if (alpha == 255 || alpha >= 64 && alpha <= 117 || alpha >= 118 && alpha <= 192 || alpha >= 201 && alpha <= 240) quadInstance.setColor(i, (color & 0xFFFFFF) | (tag << 24));
            }
        }
    }
}
