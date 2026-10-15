package naturality.client.fluids.water;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.core.BlockPos;
import com.mojang.renderpearl.api.GpuFormat;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/** Shader definitions; nested groups preserve each consumer’s initialization boundary. */
@org.jspecify.annotations.NullMarked
public final class WaterShaders {
    private WaterShaders() { }
    public static final class Intersection {
        public static final RenderPipeline.Snippet EFFECT=RenderPipeline.builder()
            .withVertexShader(Naturality.id("core/water_intersection"))
            .withFragmentShader(Naturality.id("core/water_intersection")).withCull(false).buildSnippet();
        public static final DepthStencilState DEPTH=new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false);
        public static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
            .withLocation(Naturality.id("pipeline/water_intersection")).withDepthStencilState(DEPTH)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        public static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_water_intersection",
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT))
            .withDepthBoundsModifier(b->b.withDepthStencilState(DEPTH))
            .withTransmittanceModifier(b->b.withDepthStencilState(DEPTH))
            .withAccumulateModifier(b->b.withDepthStencilState(DEPTH)).build());
        public static final SingleQuadParticle.Layer LAYER=new SingleQuadParticle.Layer(true,naturality.client.api.rendering.AtlasLocations.BLOCKS,PIPELINE,OIT);
        public static final DepthStencilState SPLASH_DEPTH=new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true);
        public static final RenderPipeline SPLASH_PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
            .withLocation(Naturality.id("pipeline/water_splash_depth")).withDepthStencilState(SPLASH_DEPTH).withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        public static final OitPipelineSet SPLASH_OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_water_splash_depth",
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT).withCull(false))
            .withDepthBoundsModifier(b->b.withDepthStencilState(SPLASH_DEPTH))
            .withTransmittanceModifier(b->b.withDepthStencilState(SPLASH_DEPTH))
            .withAccumulateModifier(b->b.withDepthStencilState(SPLASH_DEPTH)).build());
        public static final SingleQuadParticle.Layer SPLASH_LAYER=new SingleQuadParticle.Layer(true,naturality.client.api.rendering.AtlasLocations.BLOCKS,SPLASH_PIPELINE,SPLASH_OIT);
    }
    public static final class Composite {
        public static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Naturality.id("pipeline/water_optics"))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Naturality.id("core/water_optics"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
                .withBindGroupLayout(WaterVisuals.LAYOUT)
                .withBindGroupLayout(WaterVisuals.SCENE_LAYOUT)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(ColorTargetState.DEFAULT)
                .build());
        public static final RenderPipeline MASK = maskPipeline(false);
        public static final RenderPipeline MASK_MULTIDRAW = maskPipeline(true);
        public static final RenderPipeline FINISH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Naturality.id("pipeline/water_finish"))
                .withVertexShader("core/screenquad").withFragmentShader(Naturality.id("core/water_finish"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
                .withBindGroupLayout(WaterVisuals.LAYOUT).withBindGroupLayout(WaterVisuals.SCENE_LAYOUT)
                .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build());

        private static RenderPipeline maskPipeline(boolean multidraw) {
            return RenderPipelines.register(RenderPipeline.builder(multidraw ? RenderPipelines.MULTIDRAW_TERRAIN_SNIPPET : RenderPipelines.TERRAIN_SNIPPET)
                .withLocation(Naturality.id(multidraw ? "pipeline/water_mask_multidraw" : "pipeline/water_mask"))
                .withShaderDefine("NATURALITY_WATER_MASK")
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA32_FLOAT, 15)).build());
        }

    }
    public static final class Immersion {
        public static final RenderPipeline MULTIPLY = pipeline("multiply",
            new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO, BlendFactor.ZERO, BlendFactor.ONE));
        public static final RenderPipeline ADD = pipeline("add",
            new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE));

        private static RenderPipeline pipeline(String suffix, BlendFunction blend) {
            return RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Naturality.id("pipeline/water_immersion_overlay_" + suffix))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Naturality.id("core/water_immersion_overlay"))
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(blend)).build());
        }

    }

    /** Shared visible water geometry for model rims and impacts. Coordinates within a tile are in blocks. */
    public record WaterSurface(BlockPos pos,double nw,double sw,double se,double ne,boolean cauldron) {
        public double height(double x,double z) {
            return pos.getY()+(z>=x ? nw+(se-sw)*x+(sw-nw)*z : nw+(ne-nw)*x+(se-ne)*z)-(cauldron?0:0.001);
        }
        public boolean contains(double x,double z) {
            double inset=cauldron?2.0/16:0;
            return x>=inset && z>=inset && x<1-inset && z<1-inset;
        }
        public static @org.jspecify.annotations.Nullable WaterSurface at(ClientLevel level,BlockPos p) {
            if(!naturality.api.worlds.LoadedChunks.has(level, p)) return null;
            var state=level.getBlockState(p);
            if(state.is(Blocks.WATER_CAULDRON)) {
                double h=(6+3*state.getValue(LayeredCauldronBlock.LEVEL))/16.0;
                return new WaterSurface(p.immutable(),h,h,h,h,true);
            }
            if(!level.getFluidState(p).is(FluidTags.WATER) || level.getFluidState(p.above()).is(FluidTags.WATER)
                || state.isSolidRender() || level.getBlockState(p.above()).isSolidRender()) return null;
            double own=fluidHeight(level,p),n=fluidHeight(level,p.north()),s=fluidHeight(level,p.south()),
                w=fluidHeight(level,p.west()),e=fluidHeight(level,p.east());
            return new WaterSurface(p.immutable(),corner(level,p.north().west(),own,n,w),corner(level,p.south().west(),own,s,w),
                corner(level,p.south().east(),own,s,e),corner(level,p.north().east(),own,n,e),false);
        }
        private static double corner(ClientLevel level,BlockPos diagonal,double own,double a,double b) {
            if(own>=1 || a>=1 || b>=1) return 1;
            double c=a>0 || b>0?fluidHeight(level,diagonal):-1;
            if(c>=1) return 1;
            double sum=0,weight=0;
            for(double h:new double[]{own,a,b,c}) if(h>=0) {double w=h>=0.8?10:1;sum+=h*w;weight+=w;}
            return sum/weight;
        }
        @SuppressWarnings("deprecation") // Matches vanilla FluidRenderer corner heights, including partial supports.
        private static double fluidHeight(ClientLevel level,BlockPos p) {
            var fluid=level.getFluidState(p);
            return fluid.is(FluidTags.WATER)?(level.getFluidState(p.above()).is(FluidTags.WATER)?1:fluid.getOwnHeight())
                :level.getBlockState(p).isSolid()?-1:0;
        }
    }

    /** Separate shaders neutralize only water sprites, including resource-pack replacements. */
    public static final class WaterParticleTint {
        private static final RenderPipeline.Snippet EFFECT=RenderPipeline.builder()
            .withFragmentShader(Naturality.id("core/water_particle")).buildSnippet();
        private static final RenderPipeline OPAQUE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
            .withLocation(Naturality.id("pipeline/water_particle_opaque")).withColorTargetState(ColorTargetState.DEFAULT).build());
        private static final RenderPipeline TRANSLUCENT=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
            .withLocation(Naturality.id("pipeline/water_particle_translucent"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        private static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_water_particle",
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT)).build());
        public static final SingleQuadParticle.Layer SOLID=new SingleQuadParticle.Layer(false,naturality.client.api.rendering.AtlasLocations.PARTICLES,OPAQUE);
        public static final SingleQuadParticle.Layer TRANSPARENT=new SingleQuadParticle.Layer(true,naturality.client.api.rendering.AtlasLocations.PARTICLES,TRANSLUCENT,OIT);
        public static void initialize() {}
        public static boolean includes(ParticleType<?> type) {
            return type==ParticleTypes.DRIPPING_WATER || type==ParticleTypes.FALLING_WATER
                || type==ParticleTypes.DRIPPING_DRIPSTONE_WATER || type==ParticleTypes.FALLING_DRIPSTONE_WATER
                || type==ParticleTypes.RAIN || type==ParticleTypes.SPLASH || type==ParticleTypes.FISHING
                || type==ParticleTypes.BUBBLE || type==ParticleTypes.BUBBLE_POP
                || type==ParticleTypes.BUBBLE_COLUMN_UP || type==ParticleTypes.CURRENT_DOWN
                || type==ParticleTypes.UNDERWATER;
        }
        public interface Access {
            default void naturality$rainParticle(boolean rain) {}
            void naturality$waterParticle(boolean water);
            boolean naturality$isWaterParticle();
        }
    }
}
