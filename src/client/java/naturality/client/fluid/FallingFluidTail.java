package naturality.client.fluid;

import com.mojang.blaze3d.vertex.VertexConsumer;
import naturality.config.NaturalityConfig;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** A capless continuation of a falling liquid into air, with shader-stepped opacity. */
public final class FallingFluidTail {
    // Lightmap coordinates occupy only the low byte; the vertex shader strips this flag.
    public static final int LIGHT_FLAG=0x1000;
    public static final float UNDERSIDE_OFFSET=0.5F;
    @FunctionalInterface public interface LightSampler {
        int sample(float x,float y,float z,int fallback);
    }
    private FallingFluidTail() {}

    public static boolean eligible(BlockAndTintGetter level,BlockPos pos,FluidState fluid) {
        var settings=NaturalityConfig.get().liquids;
        boolean water=fluid.getType().isSame(Fluids.WATER),lava=fluid.getType().isSame(Fluids.LAVA);
        return ((water && settings.water) || (lava && settings.lava))
            && fluid.getValue(FlowingFluid.FALLING) && level.getBlockState(pos.below()).isAir();
    }

    public static void emit(BlockAndTintGetter level,BlockPos pos,BlockState block,FluidState fluid,
                            FluidModel model,FluidRenderer.Output output,LightSampler lighting) {
        if(!eligible(level,pos,fluid))return;
        // Lava normally uses the solid layer; this geometry needs true translucency.
        var vertices=output.getBuilder(ChunkSectionLayer.TRANSLUCENT);
        var sprite=model.flowingMaterial().sprite();
        int tint=model.tintSource()==null?-1:model.tintSource().colorInWorld(block,level,pos);
        float x=pos.getX()&15,y=pos.getY()&15,z=pos.getZ()&15;
        for(var side:Direction.Plane.HORIZONTAL) {
            if(level.getFluidState(pos.relative(side)).getType().isSame(fluid.getType()))continue;
            if(level.getBlockState(pos.below().relative(side)).isSolidRender())continue;
            float x0=x,x1=x+1,z0=z,z1=z+1;
            switch(side) {
                case NORTH -> {z0=z1=z+0.001F;}
                case SOUTH -> {z0=z1=z+0.999F;x0=x+1;x1=x;}
                case WEST -> {x0=x1=x+0.001F;z0=z+1;z1=z;}
                case EAST -> {x0=x1=x+0.999F;}
                default -> throw new IllegalStateException();
            }
            var cardinal=level.cardinalLighting();
            float shade=cardinal.up()*(side.getAxis()==Direction.Axis.Z?cardinal.north():cardinal.west());
            int color=ARGB.scaleRGB(tint,shade);
            int light=fluid.getType().isSame(Fluids.LAVA) && NaturalityConfig.get().liquids.emissiveLava
                ?LightCoordsUtil.FULL_BRIGHT:LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level,pos),LightCoordsUtil.getLightCoords(level,pos.below()));
            // Vanilla flowing side UVs cover half of the 32-pixel flowing sprite.
            float u0=sprite.getU(0),u1=sprite.getU(0.5F),v0=sprite.getV(0),v1=sprite.getV(0.5F);
            vertex(vertices,x0,y,z0,color,u0,v0,light,lighting);
            vertex(vertices,x1,y,z1,color,u1,v0,light,lighting);
            vertex(vertices,x1,y-1,z1,color,u1,v1,light,lighting);
            vertex(vertices,x0,y-1,z0,color,u0,v1,light,lighting);
            vertex(vertices,x0,y,z0,color,u0,v0,light,lighting);
            vertex(vertices,x0,y-1,z0,color,u0,v1,light,lighting);
            vertex(vertices,x1,y-1,z1,color,u1,v1,light,lighting);
            vertex(vertices,x1,y,z1,color,u1,v0,light,lighting);
        }
        // Preserve vanilla's downward-facing still-texture underside, recessed
        // half a block into the real falling column instead of capping its tail.
        var underside=model.stillMaterial().sprite();
        var capVertices=output.getBuilder(model.layer());
        float capY=y+UNDERSIDE_OFFSET-0.001F;
        int capColor=ARGB.scaleRGB(tint,level.cardinalLighting().down());
        int capLight=LightCoordsUtil.getLightCoords(level,pos);
        capVertex(capVertices,x,capY,z,capColor,underside.getU0(),underside.getV0(),capLight,lighting);
        capVertex(capVertices,x+1,capY,z,capColor,underside.getU1(),underside.getV0(),capLight,lighting);
        capVertex(capVertices,x+1,capY,z+1,capColor,underside.getU1(),underside.getV1(),capLight,lighting);
        capVertex(capVertices,x,capY,z+1,capColor,underside.getU0(),underside.getV1(),capLight,lighting);
    }
    private static void vertex(VertexConsumer vertices,float x,float y,float z,int color,float u,float v,int light,LightSampler lighting) {
        vertices.addVertex(x,y,z,color,u,v,OverlayTexture.NO_OVERLAY,lighting.sample(x,y,z,light)|LIGHT_FLAG,0,1,0);
    }
    private static void capVertex(VertexConsumer vertices,float x,float y,float z,int color,float u,float v,int light,LightSampler lighting) {
        vertices.addVertex(x,y,z,color,u,v,OverlayTexture.NO_OVERLAY,lighting.sample(x,y,z,light),0,1,0);
    }
}
