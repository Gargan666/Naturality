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
import net.minecraft.world.phys.Vec3;

public final class WindRendering {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder().withUniform("NaturalityWind", UniformType.UNIFORM_BUFFER).build();
    private static @org.jspecify.annotations.Nullable GpuBuffer uniform;
    private static final ThreadLocal<ExposureCache> EXPOSURE_CACHE = ThreadLocal.withInitial(ExposureCache::new);
    private static final int[] EXPOSURE_DISTANCES = {4, 8};
    private static final int[] EXPOSURE_HEIGHTS = {3};
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
        if (tag(state) != 255 && !windExposed(level, pos)) return 255;
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
        boolean result = hasOpenSkyPath(level, pos);
        if (cache.values.size() >= 4096) cache.values.clear();
        cache.values.put(key, result);
        return result;
    }
    private static boolean hasOpenSkyPath(net.minecraft.client.renderer.block.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos) {
        if (level.getBrightness(LightLayer.SKY, pos) <= 0) return false;
        Vec3 origin = new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (openSkyColumn(level, origin)) return true;
        for (int distance : EXPOSURE_DISTANCES) for (int dy : EXPOSURE_HEIGHTS)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if ((dx == 0 && dz == 0) || Math.max(Math.abs(dx), Math.abs(dz)) != 1) continue;
                Vec3 target = origin.add(dx * distance, dy, dz * distance);
                var targetPos = net.minecraft.core.BlockPos.containing(target);
                if (level.getBrightness(LightLayer.SKY, targetPos.above(5)) <= 0) continue;
                if (passable(level, targetPos) && openSkyColumn(level, target) && clearRay(level, origin, target)) return true;
            }
        return false;
    }
    private static boolean openSkyColumn(net.minecraft.client.renderer.block.BlockAndTintGetter level, Vec3 origin) {
        Vec3 top = origin.add(0, 5, 0);
        var topPos = net.minecraft.core.BlockPos.containing(top);
        return level.getBrightness(LightLayer.SKY, topPos) > 0 && passable(level, topPos)
            && clearRay(level, origin, top);
    }
    private static boolean passable(net.minecraft.client.renderer.block.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock
            || state.getCollisionShape(level, pos).isEmpty();
    }
    private static boolean clearRay(net.minecraft.client.renderer.block.BlockAndTintGetter level, Vec3 from, Vec3 to) {
        double span = Math.max(Math.max(Math.abs(to.x - from.x), Math.abs(to.y - from.y)), Math.abs(to.z - from.z));
        int steps = Math.max(1, (int)Math.ceil(span * 4));
        net.minecraft.core.BlockPos previous = null;
        for (int i = 1; i < steps; i++) {
            var sample = from.lerp(to, i / (double)steps);
            var pos = net.minecraft.core.BlockPos.containing(sample);
            if (previous != null && pos.equals(previous)) continue;
            previous = pos;
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock) continue;
            var shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty() && shape.clip(from, to, pos) != null) return false;
        }
        return true;
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




