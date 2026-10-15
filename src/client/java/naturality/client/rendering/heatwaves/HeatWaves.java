package naturality.client.rendering.heatwaves;
import naturality.config.startup.FeatureConfig.Feature;
import naturality.config.startup.FeatureConfig;
import naturality.config.live.NaturalityConfig;
import naturality.client.fluids.shared.ProceduralFluids;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import org.joml.Vector4f;

/** Loaded, nearby heat volumes; one scene copy and a depth-clipped row warp. */
@org.jspecify.annotations.NullMarked
public final class HeatWaves {
    private static final RenderPipeline PIPELINE = HeatWavePipeline.Heat.PIPELINE;

    /** Register the heat pipeline during startup, before chunks can occupy the workers. */
    public static void initialize() { }
    // Scan beyond the visible range so the five-tick/four-block scan cadence
    // discovers sources while their strength is still zero.
    private static final int LIMIT = 64, RADIUS = 28;
    private static final double FADE_START = 20, FULL_STRENGTH_DISTANCE = 8;
    private static final Comparator<AABB> ROW_ORDER = Comparator.comparingDouble((AABB b) -> b.minX)
        .thenComparingDouble(b -> b.maxX).thenComparingDouble(b -> b.minY)
        .thenComparingDouble(b -> b.maxY).thenComparingDouble(b -> b.minZ);
    private static final List<AABB> boxes = new ArrayList<>();
    private static final Set<AABB> fireBoxes = new HashSet<>();
    private static final ByteBuffer data = ByteBuffer.allocateDirect(32 + LIMIT * 32).order(ByteOrder.nativeOrder());
    private static net.minecraft.client.multiplayer.@org.jspecify.annotations.Nullable ClientLevel world;
    private static long scanned = Long.MIN_VALUE;
    private static BlockPos origin = BlockPos.ZERO;
    private static @org.jspecify.annotations.Nullable TextureTarget scene;
    private static @org.jspecify.annotations.Nullable MappableRingBuffer uniforms;
    private static float proximityStrength(AABB volume, net.minecraft.world.phys.Vec3 camera) {
        double distanceSquared = volume.distanceToSqr(camera);
        if (distanceSquared >= FADE_START * FADE_START) return 0;
        if (distanceSquared <= FULL_STRENGTH_DISTANCE * FULL_STRENGTH_DISTANCE) return 1;
        double distance = Math.sqrt(distanceSquared);
        double t = Math.clamp((FADE_START - distance) / (FADE_START - FULL_STRENGTH_DISTANCE), 0, 1);
        return (float)(t * t * (3 - 2 * t));
    }

    private static void scan(Minecraft client) {
        var level = client.level;
        if (level == null) { boxes.clear(); fireBoxes.clear(); return; }
        var center = BlockPos.containing(client.gameRenderer.mainCamera().position());
        if (world == level && level.getGameTime() - scanned < 5 && center.distSqr(origin) < 16) return;
        world = level; scanned = level.getGameTime(); origin = center; boxes.clear(); fireBoxes.clear();
        int minY = Math.max(level.getMinY(), center.getY()-RADIUS);
        int maxY = Math.min(level.getMaxY()-1, center.getY()+RADIUS);
        for (int cz = (center.getZ()-RADIUS)>>4; cz <= (center.getZ()+RADIUS)>>4; cz++)
        for (int cx = (center.getX()-RADIUS)>>4; cx <= (center.getX()+RADIUS)>>4; cx++) {
            var chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk == null) continue;
            int minX = Math.max(cx*16, center.getX()-RADIUS), maxX = Math.min(cx*16+15, center.getX()+RADIUS);
            int minZ = Math.max(cz*16, center.getZ()-RADIUS), maxZ = Math.min(cz*16+15, center.getZ()+RADIUS);
            for (int sy = minY>>4; sy <= maxY>>4; sy++) {
                var section = chunk.getSection(chunk.getSectionIndex(sy*16));
                // Test the palette once per section, not once per hot Y row.
                if (!section.maybeHas(s -> s.getFluidState().is(FluidTags.LAVA) || s.getBlock() instanceof BaseFireBlock)) continue;
                int top = Math.min(sy*16+15, maxY);
                for (int y = Math.max(sy*16, minY); y <= top; y++)
                for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++) {
                    var state = section.getBlockState(x&15, y&15, z&15);
                    boolean lava = state.getFluidState().is(FluidTags.LAVA);
                    if (!lava && !(state.getBlock() instanceof BaseFireBlock)) continue;
                    // Merge contiguous source runs, preserving the exact union of their boxes.
                    int end = x;
                    while (end < maxX) {
                        var next = section.getBlockState((end+1)&15, y&15, z&15);
                        if (lava ? !next.getFluidState().is(FluidTags.LAVA) : !(next.getBlock() instanceof BaseFireBlock)) break;
                        end++;
                    }
                    var volume = lava ? new AABB(x-2,y-2,z-2,end+3,y+3,z+3)
                        : new AABB(x,y+1,z,end+1,y+3,z+1);
                    boxes.add(volume);
                    if (!lava) fireBoxes.add(volume);
                    x = end;
                }
            }
        }
        // Coalesce rows of a lake without extending heat into missing corners.
        boxes.sort(ROW_ORDER);
        int mergedCount = 0;
        for (int i = 0; i < boxes.size();) {
            var a = boxes.get(i++);
            double endZ = a.maxZ;
            boolean fire = fireBoxes.contains(a);
            while (i < boxes.size()) {
                var b = boxes.get(i);
                if (a.minX != b.minX || a.maxX != b.maxX || a.minY != b.minY || a.maxY != b.maxY || b.minZ > endZ) break;
                endZ = Math.max(endZ, b.maxZ);
                if (fire) fireBoxes.remove(b);
                i++;
            }
            // Allocate only the completed lake/column, not an intermediate box per row.
            var merged = endZ == a.maxZ ? a : new AABB(a.minX,a.minY,a.minZ,a.maxX,a.maxY,endZ);
            if (fire && merged != a) { fireBoxes.remove(a); fireBoxes.add(merged); }
            boxes.set(mergedCount++, merged);
        }
        // Compact once instead of shifting the remaining lake rows after every merge.
        if (mergedCount < boxes.size()) boxes.subList(mergedCount, boxes.size()).clear();
        var p = client.gameRenderer.mainCamera().position();
        boxes.sort(Comparator.comparingDouble(b -> b.distanceToSqr(p)));
        if (boxes.size() > LIMIT) boxes.subList(LIMIT, boxes.size()).clear();
        fireBoxes.retainAll(boxes);
    }

    public static void render(RenderTarget target) {
        var client = Minecraft.getInstance();
        if (client.level == null || !FeatureConfig.active(Feature.VISUAL_OVERHAUL)
                || !NaturalityConfig.get().liquids.heatWaves) { boxes.clear(); fireBoxes.clear(); world = null; return; }
        int power = Math.clamp(NaturalityConfig.get().liquids.heatWavePower, 0, 200);
        if (power == 0) { world = null; return; }
        scan(client);
        var colorView = target.getColorTextureView();
        var depthView = target.getDepthTextureView();
        if (boxes.isEmpty() || colorView == null || depthView == null) return;
        var p = client.gameRenderer.mainCamera().position();
        data.clear();
        // Absolute world Y anchors the row lattice even while the camera moves/turns.
        data.putFloat(0).putFloat((float)(ProceduralFluids.ticks()/20.0 + naturality.client.systems.sleep.SleepVisuals.boostSeconds())).putFloat(power / 100f).putFloat((float)p.y);
        int active = 0;
        for (var b : boxes) {
            float strength = proximityStrength(b, p);
            if (strength == 0) continue;
            active++;
            data.putFloat((float)(b.minX-p.x)).putFloat((float)(b.minY-p.y)).putFloat((float)(b.minZ-p.z))
                .putFloat(strength);
            data.putFloat((float)(b.maxX-p.x)).putFloat((float)(b.maxY-p.y)).putFloat((float)(b.maxZ-p.z))
                .putFloat(fireBoxes.contains(b) ? 1 : 0);
        }
        if (active == 0) return;
        data.putFloat(0, active);
        // Entries after active are never read; leave that padding alone.
        data.position(data.capacity() - 16);
        // Periodic world origin preserves small-coordinate precision far from spawn.
        data.putFloat((float)(p.x - Math.floor(p.x / 512) * 512))
            .putFloat((float)(p.z - Math.floor(p.z / 512) * 512)).putFloat(0).putFloat(0);
        data.flip();
        var capturedScene = scene;
        if (capturedScene == null || capturedScene.width != target.width || capturedScene.height != target.height) {
            if (capturedScene != null) capturedScene.destroyBuffers();
            capturedScene = new TextureTarget("Naturality heat scene", target.width, target.height, GpuFormat.RGBA8_UNORM, null);
            scene = capturedScene;
        }
        capturedScene.copyColorFrom(target);
        var sceneView = capturedScene.getColorTextureView();
        if (sceneView == null) return;
        var ring = uniforms;
        if (ring == null) { ring = new MappableRingBuffer(() -> "Naturality heat volumes", 130, data.capacity()); uniforms = ring; }
        var uniform = ring.currentBuffer();
        try (var mapped = uniform.map(false, true)) { mapped.data().put(data); }
        data.rewind();
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                 () -> "Naturality pixel heat waves", colorView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1)));
            pass.setUniform("HeatVolumes", uniform);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("Sampler0", sceneView, sampler);
            pass.setUniform("Sampler1", depthView, sampler);
            pass.draw(6,1,0,0);
        } finally { ring.rotate(); }
    }
    public static void close() {
        if (scene != null) { scene.destroyBuffers(); scene = null; }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        boxes.clear(); fireBoxes.clear(); world = null; scanned = Long.MIN_VALUE;

    }
}



