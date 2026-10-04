package naturality.client.weather;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import naturality.weather.WeatherSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;

public final class WindRendering {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder().withUniform("NaturalityWind", UniformType.UNIFORM_BUFFER).build();
    private static @org.jspecify.annotations.Nullable GpuBuffer uniform;
    private static final ThreadLocal<ExposureCache> EXPOSURE_CACHE = ThreadLocal.withInitial(ExposureCache::new);
    private WindRendering() {}
    public static void clearExposureCache() {
        var cache = EXPOSURE_CACHE.get();
        cache.values.clear();
        cache.view = null;
    }
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
        if (tag(state) != 255 && !windExposed(level, pos)) return 255;
        if (connected(state)) {
            if (state.getBlock() instanceof VineBlock) {
                // Pin only the upper edge of an attached strand's first block.
                // Lower joins keep the ordinary shared wind field on both sides.
                if (y >= .999F && !level.getBlockState(pos.above()).is(state.getBlock())) {
                    boolean attached=false, movingLeaf=false;
                    if(state.getValue(VineBlock.UP)) {
                        var above=level.getBlockState(pos.above());
                        if(above.is(BlockTags.LEAVES)) movingLeaf=windExposed(level,pos.above());
                        attached=!above.is(BlockTags.LEAVES) || !movingLeaf;
                    }
                    for (var face : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        if (!state.getValue(VineBlock.getPropertyForFace(face))) continue;
                        var supportPos=pos.relative(face);
                        var support=level.getBlockState(supportPos);
                        if(support.is(BlockTags.LEAVES)) {
                            boolean moving=windExposed(level,supportPos);
                            movingLeaf |= moving;
                            attached |= !moving;
                        } else attached |= support.isFaceSturdy(level,supportPos,face.getOpposite());
                    }
                    if (attached) return 101;
                    if (movingLeaf) return 232;
                }
                int mask = solidVineSupportMask(level,pos,state);
                // All cells touching a shared vertex contribute the same constraints,
                // keeping vertical joins and wall/corner joins connected.
                for (int dx = x < .001F ? -1 : 0; dx <= (x > .999F ? 1 : 0); dx++)
                    for (int dy = y < .001F ? -1 : 0; dy <= (y > .999F ? 1 : 0); dy++)
                        for (int dz = z < .001F ? -1 : 0; dz <= (z > .999F ? 1 : 0); dz++)
                            if (dx != 0 || dy != 0 || dz != 0)
                                mask |= solidVineSupportMask(level,pos.offset(dx,dy,dz),level.getBlockState(pos.offset(dx,dy,dz)));
                return 102 + mask;
            }
            // Mark height above the shared stalk root, including identical
            // markers on both sides of each segment join.
            if (state.getBlock() instanceof SugarCaneBlock) {
                int depth=0;
                while(depth<62 && level.getBlockState(pos.below(depth+1)).is(state.getBlock())) depth++;
                return Math.clamp(depth+Math.round(y),0,63);
            }
            return 100;
        }
        return vertexTag(state,x,y,z);
    }
    public static boolean exposed(net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
        // Chunk render views may expose only their thread-safe lighting snapshot.
        return level.getBrightness(LightLayer.SKY, pos) > 0;
    }
    /** Test exposure at the foliage itself; player location must not control plants indoors. */
    public static boolean windExposed(net.minecraft.client.renderer.block.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos) {
        var cache = EXPOSURE_CACHE.get();
        if (cache.view != level) {
            cache.view = level;
            cache.values.clear();
        }
        long key = pos.asLong();
        var cached = cache.values.get(key);
        if (cached != null) return cached;
        boolean result = naturality.weather.WindShapes.raw(() -> naturality.weather.WindShelter.hasOpenSkyPath(level, pos, p -> level.getBrightness(LightLayer.SKY,p)));
        if (cache.values.size() >= 4096) cache.values.clear();
        cache.values.put(key, result);
        return result;
    }
    private static final class ExposureCache {
        private @org.jspecify.annotations.Nullable BlockAndTintGetter view;
        private final Map<Long, Boolean> values = new HashMap<>();
    }
    public static int vineSupportMask(BlockState state) {
        if (!(state.getBlock() instanceof VineBlock)) return 0;
        return (state.getValue(VineBlock.WEST) ? 1 : 0)
            | (state.getValue(VineBlock.EAST) ? 2 : 0)
            | (state.getValue(VineBlock.NORTH) ? 4 : 0)
            | (state.getValue(VineBlock.SOUTH) ? 8 : 0);
    }
    private static int solidVineSupportMask(BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state) {
        int mask=vineSupportMask(state);
        for(var face:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var supportPos=pos.relative(face);
            var support=level.getBlockState(supportPos);
            if(support.is(BlockTags.LEAVES) || !support.isFaceSturdy(level,supportPos,face.getOpposite())) {
                int bit=switch(face) { case WEST -> 1; case EAST -> 2; case NORTH -> 4; case SOUTH -> 8; default -> 0; };
                mask &= ~bit;
            }
        }
        return mask;
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
            // Crop geometry spans -1/16..15/16 above farmland. Encode which
            // integer cell each vertex occupies so every corner finds one root.
            if (state.getBlock() instanceof CropBlock) return 120
                + Math.clamp((int)Math.floor(x+.002F),0,1)
                + 2*Math.clamp((int)Math.floor(z+.002F),0,1)
                + 4*Math.clamp((int)Math.floor(y+.002F)+1,0,1);
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
        if (state.getBlock() instanceof CropBlock) return vertexTag(state,.5F,y,.5F);
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) y += 1;
        return 201 + Math.clamp(Math.round(y * 19), 0, 38);
    }
    public static void bind(RenderPass pass) {
        var client = Minecraft.getInstance();
        var level = client.level;
        var s = !naturality.config.NaturalityConfig.get().effects.foliageWind || level == null ? null : WeatherSystem.state(level);
        float windScale = s == null ? 0 : 1;
        float time = level == null ? 0 : (level.getGameTime() % 24000
            + client.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20F;
        var data = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        data.putFloat(s == null ? 0 : s.windX() * windScale).putFloat(s == null ? 0 : s.windZ() * windScale)
            .putFloat(time).putFloat(s == null ? 0 : s.wind() / 100 * windScale).flip();
        if (uniform != null) uniform.close();
        uniform = RenderSystem.getDevice().createBuffer(() -> "Naturality wind", 128, data);
        pass.setUniform("NaturalityWind", uniform);
    }
    public static void close() { if (uniform != null) { uniform.close(); uniform = null; } }
}
