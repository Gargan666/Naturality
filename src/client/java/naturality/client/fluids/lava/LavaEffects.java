package naturality.client.fluids.lava;

import naturality.client.fluids.shared.ProceduralFluids;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.oit.OitPipelineSet;

/** Coherent rising molten lobes and dark seams, distinct from water's streak field. */
@org.jspecify.annotations.NullMarked
public final class LavaEffects {
    public static float heat(double x,double y,double age) {
        double bend=Math.sin(y*.23-age*.09)*2.3;
        double lobes=Math.sin((x+bend)*.55)+Math.cos(y*.34-age*.16)+.5*Math.sin(x*.31-y*.21);
        return (float)Math.clamp(.48+lobes*.17,0,1);
    }
    public static int brightColor() {
        return naturality.client.blocks.fire.ProceduralFire.FirePalette.color(1F);
    }
    private static int mix(int a,int b,float amount) {
        int r=Math.round(net.minecraft.util.ARGB.red(a)*(1-amount)+net.minecraft.util.ARGB.red(b)*amount);
        int g=Math.round(net.minecraft.util.ARGB.green(a)*(1-amount)+net.minecraft.util.ARGB.green(b)*amount);
        int blue=Math.round(net.minecraft.util.ARGB.blue(a)*(1-amount)+net.minecraft.util.ARGB.blue(b)*amount);
        return 0xff000000 | r<<16 | g<<8 | blue;
    }
    public static int color(float heat,float tip) {
        int base=ProceduralFluids.splashLavaColor(Math.clamp(.08F+.68F*heat,0,.94F));
        return mix(base,brightColor(),Math.clamp(tip,0,1));
    }

    /** Shared visible water geometry for model rims and impacts. Coordinates within a tile are in blocks. */
    public record LavaSurface(BlockPos pos,double nw,double sw,double se,double ne,boolean cauldron) {
        public double height(double x,double z) {
            return pos.getY()+(z>=x ? nw+(se-sw)*x+(sw-nw)*z : nw+(ne-nw)*x+(se-ne)*z)-(cauldron?0:0.001);
        }
        public boolean contains(double x,double z) {
            double inset=cauldron?2.0/16:0;
            return x>=inset && z>=inset && x<1-inset && z<1-inset;
        }
        public static @org.jspecify.annotations.Nullable LavaSurface at(ClientLevel level,BlockPos p) {
            if(!naturality.api.worlds.LoadedChunks.has(level, p)) return null;
            var state=level.getBlockState(p);
            if(state.is(Blocks.LAVA_CAULDRON)) {
                double h=15/16.0;
                return new LavaSurface(p.immutable(),h,h,h,h,true);
            }
            if(!level.getFluidState(p).is(FluidTags.LAVA) || level.getFluidState(p.above()).is(FluidTags.LAVA)
                || state.isSolidRender() || level.getBlockState(p.above()).isSolidRender()) return null;
            double own=fluidHeight(level,p),n=fluidHeight(level,p.north()),s=fluidHeight(level,p.south()),
                w=fluidHeight(level,p.west()),e=fluidHeight(level,p.east());
            return new LavaSurface(p.immutable(),corner(level,p.north().west(),own,n,w),corner(level,p.south().west(),own,s,w),
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
            return fluid.is(FluidTags.LAVA)?(level.getFluidState(p.above()).is(FluidTags.LAVA)?1:fluid.getOwnHeight())
                :level.getBlockState(p).isSolid()?-1:0;
        }
    }

    /** Shader definitions; nested groups preserve each consumer’s initialization boundary. */
    public static final class LavaShaders {
        private LavaShaders() { }
        public static final class Intersection {
            public static final RenderPipeline.Snippet EFFECT=RenderPipeline.builder()
                .withVertexShader(Naturality.id("core/water_intersection"))
                .withFragmentShader(Naturality.id("core/water_intersection")).withCull(false).buildSnippet();
            public static final DepthStencilState DEPTH=new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false);
            public static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
                .withLocation(Naturality.id("pipeline/lava_intersection")).withDepthStencilState(DEPTH)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
            public static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_lava_intersection",
                RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT))
                .withDepthBoundsModifier(b->b.withDepthStencilState(DEPTH))
                .withTransmittanceModifier(b->b.withDepthStencilState(DEPTH))
                .withAccumulateModifier(b->b.withDepthStencilState(DEPTH)).build());
            public static final SingleQuadParticle.Layer LAYER=new SingleQuadParticle.Layer(true,naturality.client.api.rendering.AtlasLocations.BLOCKS,PIPELINE,OIT);
            public static final DepthStencilState SPLASH_DEPTH=new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true);
            public static final RenderPipeline SPLASH_PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
                .withLocation(Naturality.id("pipeline/lava_splash_depth")).withDepthStencilState(SPLASH_DEPTH).withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
            public static final OitPipelineSet SPLASH_OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_lava_splash_depth",
                RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT).withCull(false))
                .withDepthBoundsModifier(b->b.withDepthStencilState(SPLASH_DEPTH))
                .withTransmittanceModifier(b->b.withDepthStencilState(SPLASH_DEPTH))
                .withAccumulateModifier(b->b.withDepthStencilState(SPLASH_DEPTH)).build());
            public static final SingleQuadParticle.Layer SPLASH_LAYER=new SingleQuadParticle.Layer(true,naturality.client.api.rendering.AtlasLocations.BLOCKS,SPLASH_PIPELINE,SPLASH_OIT);
            static {
                naturality.client.api.rendering.ShaderPacks.registerFlatParticles(PIPELINE, OIT);
                naturality.client.api.rendering.ShaderPacks.registerFlatParticles(SPLASH_PIPELINE, SPLASH_OIT);
            }
        }
    }
}
