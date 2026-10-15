// Frozen pre-optimization renderer for volume and pixel equivalence checks.
package naturality.test.reference;
import naturality.test.TestFixtures;

import naturality.client.fluid.ProceduralFluids;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.*;
import java.util.*;
import naturality.Naturality;
import naturality.config.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import org.joml.Vector4f;

/** Loaded, nearby heat volumes; one scene copy and a depth-clipped row warp. */
public final class ReferenceHeatWaves {
    // Scan beyond the visible range so the five-tick/four-block scan cadence
    // discovers sources while their strength is still zero.
    private static final int LIMIT = 64, RADIUS = 28;
    private static final double FADE_START = 20, FULL_STRENGTH_DISTANCE = 8;
    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
        .withUniform("HeatVolumes", UniformType.UNIFORM_BUFFER).build();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id("pipeline/reference_heat_waves"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Naturality.id("core/reference_heat_waves"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withBindGroupLayout(LAYOUT).withDepthStencilState(Optional.empty())
            .withColorTargetState(ColorTargetState.DEFAULT).build());
    private static final List<AABB> boxes = new ArrayList<>();
    private static final Set<AABB> fireBoxes = new HashSet<>();
    private static final ByteBuffer data = ByteBuffer.allocateDirect(32 + LIMIT * 32).order(ByteOrder.nativeOrder());
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static long scanned = Long.MIN_VALUE;
    private static BlockPos origin = BlockPos.ZERO;
    private static TextureTarget scene;
    private static float proximityStrength(AABB volume, net.minecraft.world.phys.Vec3 camera) {
        double distance = Math.sqrt(volume.distanceToSqr(camera));
        double t = Math.clamp((FADE_START - distance) / (FADE_START - FULL_STRENGTH_DISTANCE), 0, 1);
        return (float)(t * t * (3 - 2 * t));
    }

    private static void scan(Minecraft client) {
        var level = TestFixtures.require(client.level, "level");
        var center = BlockPos.containing(client.gameRenderer.mainCamera().position());
        if (world == level && level.getGameTime() - scanned < 5 && center.distSqr(origin) < 16) return;
        world = level; scanned = level.getGameTime(); origin = center; boxes.clear(); fireBoxes.clear();
        var cursor = new BlockPos.MutableBlockPos();
        for (int cz = (center.getZ()-RADIUS)>>4; cz <= (center.getZ()+RADIUS)>>4; cz++)
        for (int cx = (center.getX()-RADIUS)>>4; cx <= (center.getX()+RADIUS)>>4; cx++) {
            var chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk == null) continue;
            for (int y = Math.max(level.getMinY(), center.getY()-RADIUS); y <= Math.min(level.getMaxY()-1, center.getY()+RADIUS); y++) {
                var section = chunk.getSection(chunk.getSectionIndex(y));
                if (!section.maybeHas(s -> s.getFluidState().is(FluidTags.LAVA) || s.getBlock() instanceof BaseFireBlock)) { y = (y | 15); continue; }
                for (int z = Math.max(cz*16, center.getZ()-RADIUS); z <= Math.min(cz*16+15, center.getZ()+RADIUS); z++)
                for (int x = Math.max(cx*16, center.getX()-RADIUS); x <= Math.min(cx*16+15, center.getX()+RADIUS); x++) {
                    cursor.set(x,y,z);
                    var state = chunk.getBlockState(cursor);
                    boolean lava = state.getFluidState().is(FluidTags.LAVA);
                    if (!lava && !(state.getBlock() instanceof BaseFireBlock)) continue;
                    // Merge contiguous source runs, preserving the exact union of their boxes.
                    int end = x;
                    while (end < Math.min(cx*16+15, center.getX()+RADIUS)) {
                        cursor.set(end+1,y,z);
                        var next = chunk.getBlockState(cursor);
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
        boxes.sort(Comparator.comparingDouble((AABB b) -> b.minX).thenComparingDouble(b -> b.maxX)
            .thenComparingDouble(b -> b.minY).thenComparingDouble(b -> b.maxY).thenComparingDouble(b -> b.minZ));
        for (int i = 1; i < boxes.size();) {
            var a = boxes.get(i-1); var b = boxes.get(i);
            if (a.minX == b.minX && a.maxX == b.maxX && a.minY == b.minY && a.maxY == b.maxY && b.minZ <= a.maxZ) {
                var merged = new AABB(a.minX,a.minY,a.minZ,a.maxX,a.maxY,Math.max(a.maxZ,b.maxZ));
                if (fireBoxes.remove(a)) { fireBoxes.remove(b); fireBoxes.add(merged); }
                boxes.set(i-1, merged);
                boxes.remove(i);
            } else i++;
        }
        var p = client.gameRenderer.mainCamera().position();
        boxes.sort(Comparator.comparingDouble(b -> b.distanceToSqr(p)));
        if (boxes.size() > LIMIT) boxes.subList(LIMIT, boxes.size()).clear();
        fireBoxes.retainAll(boxes);
    }

    public static void render(RenderTarget target) {
        var client = Minecraft.getInstance();
        if (client.level == null || !FeatureConfig.active(Feature.VISUAL_OVERHAUL)
                || !NaturalityConfig.get().liquids.heatWaves) { boxes.clear(); fireBoxes.clear(); world = null; return; }
        scan(client);
        int power = Math.clamp(NaturalityConfig.get().liquids.heatWavePower, 0, 200);
        if (power == 0) return;
        if (boxes.isEmpty() || target.getColorTextureView() == null || target.getDepthTextureView() == null) return;
        var p = client.gameRenderer.mainCamera().position();
        if (boxes.stream().noneMatch(b -> proximityStrength(b, p) > 0)) return;
        if (scene == null || scene.width != target.width || scene.height != target.height) {
            if (scene != null) scene.destroyBuffers();
            scene = new TextureTarget("Naturality heat scene", target.width, target.height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        }
        scene.copyColorFrom(target);
        data.clear();
        // Absolute world Y anchors the row lattice even while the camera moves/turns.
        data.putFloat(boxes.size()).putFloat(ProceduralFluids.ticks()/20f).putFloat(power / 100f).putFloat((float)p.y);
        for (var b : boxes) {
            data.putFloat((float)(b.minX-p.x)).putFloat((float)(b.minY-p.y)).putFloat((float)(b.minZ-p.z))
                .putFloat(proximityStrength(b, p));
            data.putFloat((float)(b.maxX-p.x)).putFloat((float)(b.maxY-p.y)).putFloat((float)(b.maxZ-p.z))
                .putFloat(fireBoxes.contains(b) ? 1 : 0);
        }
        while (data.position() < data.capacity() - 16) data.putFloat(0);
        // Periodic world origin preserves small-coordinate precision far from spawn.
        data.putFloat((float)(p.x - Math.floor(p.x / 512) * 512))
            .putFloat((float)(p.z - Math.floor(p.z / 512) * 512)).putFloat(0).putFloat(0);
        data.flip();
        try (var uniform = RenderSystem.getDevice().createBuffer(() -> "Naturality heat volumes", 128, data);
             var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                 () -> "Naturality pixel heat waves", TestFixtures.require(target.getColorTextureView(), "target.getColorTextureView()"), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1)));
            pass.setUniform("HeatVolumes", uniform);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("Sampler0", scene.getColorTextureView(), sampler);
            pass.setUniform("Sampler1", target.getDepthTextureView(), sampler);
            pass.draw(3,1,0,0);
        }
    }
    public static void close() {
        if (scene != null) { scene.destroyBuffers(); scene = null; }
        boxes.clear(); fireBoxes.clear(); world = null; scanned = Long.MIN_VALUE;

    }
}



