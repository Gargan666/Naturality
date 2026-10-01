package naturality.client.weather;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import naturality.weather.WeatherSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;

public final class WindRendering {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder().withUniform("NaturalityWind", UniformType.UNIFORM_BUFFER).build();
    private static @org.jspecify.annotations.Nullable GpuBuffer uniform;
    private WindRendering() {}
    /** Reserved tint-alpha markers, below the water occupancy range 246..254. */
    public static int tag(BlockState state) {
        if (connected(state)) return 100;
        if (state.is(BlockTags.LEAVES)) return 240;
        if (state.getBlock() instanceof VegetationBlock && !state.is(Blocks.LILY_PAD)) return 241;
        return 255;
    }
    private static boolean connected(BlockState state) {
        return state.getBlock() instanceof SugarCaneBlock || state.getBlock() instanceof VineBlock
            || state.getBlock() instanceof GrowingPlantBlock;
    }
    /** Connected segments sample one continuous world-space field at their shared edges. */
    public static int vertexTag(net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos,
            BlockState state, float x, float y, float z) {
        if (tag(state) != 255 && level.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(pos) == 0) return 255;
        if (connected(state)) {
            if (state.getBlock() instanceof VineBlock) {
                int mask = vineSupportMask(state);
                // All cells touching a shared vertex contribute the same constraints,
                // keeping vertical joins and wall/corner joins connected.
                for (int dx = x < .001F ? -1 : 0; dx <= (x > .999F ? 1 : 0); dx++)
                    for (int dy = y < .001F ? -1 : 0; dy <= (y > .999F ? 1 : 0); dy++)
                        for (int dz = z < .001F ? -1 : 0; dz <= (z > .999F ? 1 : 0); dz++)
                            if (dx != 0 || dy != 0 || dz != 0)
                                mask |= vineSupportMask(level.getBlockState(pos.offset(dx,dy,dz)));
                return 102 + mask;
            }
            // Only the bottom of a cane column is anchored, never each segment's bottom.
            if (state.getBlock() instanceof SugarCaneBlock && y < .001F
                    && !level.getBlockState(pos.below()).is(state.getBlock())) return 101;
            return 100;
        }
        return vertexTag(state,x,y,z);
    }
    public static boolean exposed(net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
        return level.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(pos) > 0;
    }
    public static int vineSupportMask(BlockState state) {
        if (!(state.getBlock() instanceof VineBlock)) return 0;
        return (state.getValue(VineBlock.WEST) ? 1 : 0)
            | (state.getValue(VineBlock.EAST) ? 2 : 0)
            | (state.getValue(VineBlock.NORTH) ? 4 : 0)
            | (state.getValue(VineBlock.SOUTH) ? 8 : 0);
    }
    /** Leaf vertices and their snow share one block-center phase, including boundary vertices. */
    public static int leafTag(float x,float y,float z) {
        return 160 + Math.clamp((int)Math.floor(x+.002F),0,1)
            + 2*Math.clamp((int)Math.floor(z+.002F),0,1)
            + 4*Math.clamp((int)Math.floor(y+.002F),0,7);
    }
    /** Coordinates include the state model offset, matching positions seen by the shader. */
    public static int vertexTag(BlockState state,float x,float y,float z) {
        if (state.is(BlockTags.LEAVES)) return leafTag(x,y,z);
        if(tag(state)==241) {
            int upper=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    ==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? 1 : 0;
            return 64 + Math.clamp((int)Math.floor(x+.002F)+1,0,2)
                + 3*Math.clamp((int)Math.floor(z+.002F)+1,0,2)
                + 9*Math.clamp((int)Math.floor(y+.002F)+upper+1,0,3);
        }
        return vertexTag(state,y);
    }
    public static int vertexTag(BlockState state, float y) {
        if (tag(state) != 241) return tag(state);
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) y += 1;
        return 201 + Math.clamp(Math.round(y * 19), 0, 38);
    }
    public static void bind(RenderPass pass) {
        var client = Minecraft.getInstance();
        var level = client.level;
        var s = !naturality.config.NaturalityConfig.get().effects.foliageWind || level == null ? null : WeatherSystem.state(level);
        float time = level == null ? 0 : (level.getGameTime() % 24000
            + client.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20F;
        var data = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        data.putFloat(s == null ? 0 : s.windX()).putFloat(s == null ? 0 : s.windZ())
            .putFloat(time).putFloat(s == null ? 0 : s.wind() / 100).flip();
        if (uniform != null) uniform.close();
        uniform = RenderSystem.getDevice().createBuffer(() -> "Naturality wind", 128, data);
        pass.setUniform("NaturalityWind", uniform);
    }
    public static void close() { if (uniform != null) { uniform.close(); uniform = null; } }
}




