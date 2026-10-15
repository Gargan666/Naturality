package naturality.client.fluids.water.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.BubbleParticle;
import net.minecraft.client.particle.BubbleColumnUpParticle;
import net.minecraft.client.particle.WaterCurrentDownParticle;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.fluids.water.WaterShaders.WaterSurface;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import naturality.client.mixins.particles.ParticleChangesMixin.ParticleSpriteAccess;
import naturality.client.fluids.water.WaterComposite;
import naturality.client.fluids.water.WaterVisuals;
import naturality.client.fluids.shared.ProceduralFluids;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import naturality.Naturality;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Pseudo;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.particle.DripParticle;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.client.particle.WaterDropParticle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import naturality.config.live.NaturalityConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import naturality.client.fluids.water.WaterComposite.WaterImmersionOverlay;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import naturality.client.fluids.water.WaterShaders.WaterParticleTint;
import naturality.client.systems.weather.framework.WeatherClient.WeatherParticleContext;
import net.minecraft.client.particle.ParticleEngine;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class WaterMixins {
    private WaterMixins() {}

    /** Spawn vanilla's animated, water-tinted pop whenever a bubble is removed. */
    @Mixin(Particle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BubblePopTransitionMixin {
        @Unique private boolean naturality$popEmitted;

        @Inject(method = "remove", at = @At("HEAD"))
        private void naturality$popOnRemoval(CallbackInfo ci) {
            if (!naturality.config.live.NaturalityConfig.get().effects.bubblePops) return;
            if (!naturality$popEmitted && ((Object) this instanceof BubbleParticle
                || (Object) this instanceof BubbleColumnUpParticle
                || (Object) this instanceof WaterCurrentDownParticle)) {
                naturality$popEmitted = true;
                var level = Minecraft.getInstance().level;
                if (level != null) {
                    var position = ((Particle) (Object) this).getBoundingBox().getCenter();
                    level.addParticle(ParticleTypes.BUBBLE_POP,
                        position.x, position.y, position.z, 0, 0, 0);
                }
            }
        }
    }

    /** A water-tagged block can still be air above its sloped surface. */
    @Mixin(BubbleParticle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BubbleSurfaceHeightMixin extends SingleQuadParticle {
        protected BubbleSurfaceHeightMixin(ClientLevel level,double x,double y,double z,TextureAtlasSprite sprite){super(level,x,y,z,sprite);}
        @Inject(method="tick",at=@At("TAIL"))
        private void naturality$leaveSurface(CallbackInfo ci) {
            if(!naturality.config.live.NaturalityConfig.get().liquids.water)return;
            var surface=WaterSurface.at(level,BlockPos.containing(x,y,z));
            if(surface!=null && y>=surface.height(x-surface.pos().getX(),z-surface.pos().getZ()))remove();
        }
    }

    /** Keep individual texture pixels the same world-space size for bubbles from all vanilla sources. */
    @Mixin({BubbleParticle.class, BubbleColumnUpParticle.class, WaterCurrentDownParticle.class})
    @org.jspecify.annotations.NullMarked
    public static abstract class FixedBubbleScaleMixin extends SingleQuadParticle {
        private static final float NATURALITY_BUBBLE_PIXEL_SIZE = 0.1F / 7.0F;

        protected FixedBubbleScaleMixin(ClientLevel level, double x, double y, double z,
                                        TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
        }

        @Inject(method = "<init>", at = @At("RETURN"))
        private void naturality$fixBubbleScale(CallbackInfo ci) {
            if (!naturality.config.live.NaturalityConfig.get().effects.fixedBubbleScale) return;
            var sprite = ((ParticleSpriteAccess)this).naturality$sprite();
            // The vanilla bubble is 7x7, so keep its existing pixel scale and size
            // every variant's quad from that sprite's actual pixel dimensions.
            quadSize = NATURALITY_BUBBLE_PIXEL_SIZE
                * Math.max(sprite.contents().width(), sprite.contents().height());
        }
    }

    @Pseudo
    @Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap=false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumWaterPipelineMixin {
        @SuppressWarnings("null") @Shadow @Final protected VertexFormat vertexFormat;
        @Unique private @org.jspecify.annotations.Nullable RenderPipeline naturality$mask;

        @ModifyExpressionValue(method={"createShader", "createOITShader"}, at=@At(value="INVOKE",
            target="Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withFragmentShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
        private RenderPipeline.Builder naturality$waterShaders(RenderPipeline.Builder builder) {
            return naturality$shaders(builder);
        }

        @Unique private static RenderPipeline.Builder naturality$shaders(RenderPipeline.Builder builder) {
            return builder.withVertexShader(Naturality.id("core/sodium_water"))
                .withBindGroupLayout(naturality.client.ui.menus.previews.MenuSurface.MenuFog.LAYOUT)
                .withFragmentShader("core/terrain").withShaderDefine("NATURALITY_SODIUM")
                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.FOG)
                .withBindGroupLayout(ProceduralFluids.LAYOUT)
                .withBindGroupLayout(naturality.client.blocks.fire.ProceduralFire.SODIUM_LAYOUT)
                .withBindGroupLayout(naturality.client.systems.weather.wind.WindRendering.LAYOUT)
                .withBindGroupLayout(WaterVisuals.LAYOUT)
                .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("NaturalityWaterAtmosphere", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("NaturalityWaterOpaqueDepth", UniformType.COMBINED_IMAGE_SAMPLER).build());
        }

        @Inject(method="compileProgram", at=@At("HEAD"), cancellable=true)
        private void naturality$maskPipeline(CallbackInfoReturnable<RenderPipeline> ci) {
            if (!WaterComposite.drawingMask()) return;
            if (naturality$mask==null) {
                var globals=BindGroupLayout.builder()
                    .withUniform("u_BlockTex", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("u_Globals", UniformType.UNIFORM_BUFFER)
                    .withUniform("u_SectionTimeInfo", UniformType.TEXEL_BUFFER, GpuFormat.R32_SINT).build();
                var light=BindGroupLayout.builder().withUniform("u_LightTex", UniformType.COMBINED_IMAGE_SAMPLER).build();
                naturality$mask=naturality$shaders(RenderPipeline.builder().withLocation(Naturality.id("pipeline/sodium_water_mask"))
                    .withBindGroupLayout(globals).withBindGroupLayout(light).withPushConstantSize(20)
                    .withVertexBinding(0,vertexFormat).withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(true).withShaderDefine("USE_VERTEX_COMPRESSION").withShaderDefine("NATURALITY_WATER_MASK")
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true))
                    .withColorTargetState(new ColorTargetState(java.util.Optional.empty(),GpuFormat.RGBA32_FLOAT,15))).build();
            }
            ci.setReturnValue(naturality$mask);
        }
    }

    @Pseudo
    @Mixin(targets="net.caffeinemc.mods.sodium.client.util.SodiumChunkSection", remap=false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumWaterSectionMixin implements naturality.client.fluids.water.WaterComposite.SodiumWaterBridge { }

    @Pseudo
    @Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap=false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumWaterUniformMixin {
        @Inject(method="render",at=@At("HEAD"))
        private void naturality$bind(CallbackInfo ci,@Local(argsOnly=true) RenderPass pass) {
            com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
            naturality.client.fluids.shared.ProceduralFluids.bind(pass);
            naturality.client.blocks.fire.ProceduralFire.bindSodium(pass);
            naturality.client.systems.weather.wind.WindRendering.bind(pass);
            naturality.client.ui.menus.previews.MenuSurface.MenuFog.bind(pass);
            naturality.client.fluids.water.WaterVisuals.bind(pass);
            naturality.client.fluids.water.WaterVisuals.bindScene(pass);
        }
    }

    @Mixin(ClientChunkCache.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterBiomeCacheMixin {
        @Inject(method = "replaceBiomes", at = @At("TAIL"))
        private void naturality$refreshWaterBiomes(CallbackInfo ci) { WaterVisuals.invalidateBiomes(); }
    }

    @Mixin(DripParticle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterDripImpactMixin extends SingleQuadParticle {
        @SuppressWarnings("null") @Shadow @Final private Fluid type;
        @SuppressWarnings("null") // The target constructor initializes this shadow field.
        protected WaterDripImpactMixin(ClientLevel level,double x,double y,double z,TextureAtlasSprite sprite) {
            super(level,x,y,z,sprite);
        }
        @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/client/particle/DripParticle;postMoveUpdate()V"),cancellable=true)
        private void naturality$impact(CallbackInfo ci) {
            if(gravity>0.01F && (type==Fluids.WATER || type==Fluids.LAVA) && naturality.client.fluids.shared.FallingFluidTail.LiquidDropletContact.impact(level,type==Fluids.LAVA,xo,yo,zo,x,y,z)) {
                remove();
                ci.cancel();
            }
        }
    }

    @Mixin(WaterDropParticle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterDropImpactMixin extends SingleQuadParticle implements naturality.client.fluids.shared.FallingFluidTail.ImpactDroplet {
        @Unique private boolean naturality$ring=true;
        @Override public void naturality$impactRing(boolean enabled){naturality$ring=enabled;}
        @Unique private boolean naturality$hit;
        @Unique private boolean naturality$untilLanding;
        @Unique private int naturality$rainDuration;
        @Unique private int naturality$rainAge;
        @Override public void naturality$rainSplash() {
            if (!naturality.config.live.NaturalityConfig.get().effects.rainSplashFade) return;
            var pos=net.minecraft.core.BlockPos.containing(x,y-0.16,z);
            var block=level.getBlockState(pos);
            if(!block.getFluidState().isEmpty()) return;
            double top=block.getCollisionShape(level,pos).max(net.minecraft.core.Direction.Axis.Y,
                x-pos.getX(),z-pos.getZ())+pos.getY();
            if(!Double.isFinite(top) || Math.abs(y-top)>0.15) return;
            // Predict the vanilla ballistic return, ending before its collision tick.
            double height=y, velocity=yd;
            int ticks=0;
            do {
                velocity-=gravity;
                height+=velocity;
                velocity*=0.98;
                ticks++;
            } while(height>top && ticks<80);
            naturality$rainDuration=Math.max(1,ticks-1);
            lifetime=Math.max(lifetime,naturality$rainDuration+1);
        }
        @Override public void naturality$waterDroplet(){}
        @Override public void naturality$landBeforeExpiring(){naturality$untilLanding=true;}
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private void naturality$keepAirborne(CallbackInfo ci) {
            if (!naturality.config.live.NaturalityConfig.get().effects.rainSplashFade && naturality$rainDuration>0) { naturality$rainDuration=0; alpha=1; }
            if(naturality$rainDuration>0) {
                float progress=++naturality$rainAge/(float)naturality$rainDuration;
                float fade=Math.clamp((progress-0.25F)/0.75F,0,1);
                alpha=1-fade*fade*(3-2*fade);
                if(naturality$rainAge>=naturality$rainDuration) {remove();ci.cancel();return;}
            }
            if(naturality$untilLanding && !onGround) lifetime=Math.max(lifetime,2);
        }
        @Inject(method="getLayer",at=@At("HEAD"),cancellable=true)
        private void naturality$fadeLayer(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Layer> cir) {
            if(naturality$rainDuration>0) cir.setReturnValue(Layer.TRANSLUCENT);
        }
        protected WaterDropImpactMixin(ClientLevel level,double x,double y,double z,TextureAtlasSprite sprite) {
            super(level,x,y,z,sprite);
        }
        @Inject(method="tick",at=@At("TAIL"))
        private void naturality$impact(CallbackInfo ci) {
            if(!naturality$hit && naturality.client.fluids.shared.FallingFluidTail.LiquidDropletContact.impact(level,false,xo,yo,zo,x,y,z,naturality$ring)) {
                naturality$hit=true;
                remove();
            }
        }
    }

    /** Vanilla entry bursts assume a full block; use the rendered surface at each random spawn point. */
    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterEntryParticleHeightMixin {
        @WrapOperation(method="doWaterSplashEffect",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$surfaceHeight(Level level,ParticleOptions type,double x,double y,double z,
                double vx,double vy,double vz,Operation<Void> original) {
            if(level instanceof ClientLevel client && naturality.config.live.NaturalityConfig.get().liquids.water) {
                var pos=BlockPos.containing(x,y-1,z);
                for(int offset=0;offset>=-1;offset--) {
                    var surface=WaterSurface.at(client,pos.above(offset));
                    if(surface==null || !surface.contains(x-surface.pos().getX(),z-surface.pos().getZ()))continue;
                    y=surface.height(x-surface.pos().getX(),z-surface.pos().getZ())
                        +(type.getType()==ParticleTypes.BUBBLE?-0.02:0.01);
                    break;
                }
            }
            original.call(level,type,x,y,z,vx,vy,vz);
        }
    }

    @Mixin(WaterFogEnvironment.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterFogMixin {
        @Inject(method = "setupFog", at = @At("TAIL"))
        private void naturality$directionalFog(FogData fog, Camera camera, ClientLevel level,
                float distance, DeltaTracker delta, CallbackInfo ci) {
            if (!WaterVisuals.ready() || !NaturalityConfig.get().liquids.waterDepth) return;
            // The composite supplies water extinction. Keep distance culling, but
            // remove the isotropic water wall that would obscure the upward window.
            fog.environmentalStart = 1.0e6F;
            fog.environmentalEnd = 1.0e6F;
            fog.skyEnd = distance;
            fog.cloudEnd = distance;
        }
    }

    @Mixin(GameRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterImmersionOverlayMixin {
        @Inject(method = "renderLevel", at = @At("TAIL"))
        private void naturality$immersionOverlay(CallbackInfo ci) {
            WaterImmersionOverlay.render();
        }
    }

    @Mixin(ChunkSectionsToRender.class)
    @org.jspecify.annotations.NullMarked
    public interface WaterLayersAccess {
        @Accessor("terrainTransformUBO")
        @org.jspecify.annotations.Nullable GpuBufferSlice naturality$terrainTransform();

        @Invoker("renderLayers")
        void naturality$renderWaterMask(ChunkSectionLayer[] layers, GpuSampler sampler, RenderPass pass,
            GpuTextureView atlas, GpuTextureView lightmap, RenderPipeline pipeline, RenderPipeline multidraw);
    }

    /** The immersion overlay supplies the persistent biome tint, including spectators.
     * Do not stack vanilla's fixed-blue texture over it in first person. */
    @Mixin(ScreenEffectRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterOverlayMixin {
        @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
        private static void naturality$useBiomeImmersionTint(CallbackInfo ci) {
            if (WaterImmersionOverlay.active()) ci.cancel();
        }
    }

    @Mixin(SingleQuadParticle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterParticleTintMixin extends Particle implements WaterParticleTint.Access {
        @Unique private boolean naturality$water;
        @Unique private boolean naturality$rain;
        @Override public void naturality$rainParticle(boolean rain) {naturality$rain=rain;}
        protected WaterParticleTintMixin(ClientLevel level,double x,double y,double z) {super(level,x,y,z);}
        @Override public void naturality$waterParticle(boolean water) {naturality$water=water;}
        @Override public boolean naturality$isWaterParticle() {return naturality$water;}
        @ModifyExpressionValue(method="extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/particle/SingleQuadParticle;getLayer()Lnet/minecraft/client/particle/SingleQuadParticle$Layer;"))
        private SingleQuadParticle.Layer naturality$tintLayer(SingleQuadParticle.Layer layer) {
            if(!naturality$water || !NaturalityConfig.get().liquids.water || !NaturalityConfig.get().liquids.waterParticleTint)return layer;
            return naturality$rain ? naturality.client.systems.weather.overworld.rain.RainParticleTint.TRANSPARENT
                : layer.translucent()?WaterParticleTint.TRANSPARENT:WaterParticleTint.SOLID;
        }
        @ModifyExpressionValue(method="extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"))
        private int naturality$tintColor(int original) {
            if(!naturality$water || !NaturalityConfig.get().liquids.water || !NaturalityConfig.get().liquids.waterParticleTint)return original;
            int tint=WeatherParticleContext.waterTint(level,BlockPos.containing(x,y,z));
            return (original & 0xff000000) | (tint & 0xffffff);
        }
    }

    @Mixin(ParticleEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterParticleTypeMixin {
        @Inject(method="makeParticle",at=@At("RETURN"))
        private void naturality$markWater(ParticleOptions options,double x,double y,double z,double vx,double vy,double vz,
                                         CallbackInfoReturnable<Particle> cir) {
            naturality.client.systems.weather.wind.WindRendering.WindParticleControl.mark(cir.getReturnValue(),
                net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.wrapAsHolder(options.getType())
                    .is(naturality.bootstrap.ContentRegistration.NaturalityTags.NO_WIND_EFFECT));
            if(options.getType()==net.minecraft.core.particles.ParticleTypes.SPLASH && cir.getReturnValue() instanceof naturality.client.fluids.shared.FallingFluidTail.ImpactDroplet droplet)
                droplet.naturality$waterDroplet();
            if(options.getType()==net.minecraft.core.particles.ParticleTypes.RAIN && cir.getReturnValue() instanceof naturality.client.fluids.shared.FallingFluidTail.ImpactDroplet rain)
                rain.naturality$rainSplash();
            if(cir.getReturnValue() instanceof WaterParticleTint.Access particle) {
                particle.naturality$waterParticle(WaterParticleTint.includes(options.getType()));
                particle.naturality$rainParticle(options.getType()==net.minecraft.core.particles.ParticleTypes.RAIN);
            }
        }
    }
}
