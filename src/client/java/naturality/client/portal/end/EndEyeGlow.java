package naturality.client.portal.end;

import java.util.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Only vanilla insertion events create entries; existing eyes never auto-start. */
public final class EndEyeGlow {
    public static final int DURATION_TICKS = 40;
    private static final class Flash {
        final long start;
        boolean seenEye;
        Flash(long start) { this.start = start; }
    }
    private static final Map<BlockPos, Flash> ACTIVE = new LinkedHashMap<>();
    private static @org.jspecify.annotations.Nullable ClientLevel currentLevel;

    public static void start(ClientLevel level, BlockPos pos) {
        if (!naturality.config.NaturalityConfig.get().portalChanges.eyeGlow) return;
        useLevel(level);
        // The level-event packet can precede the chunk's block-state update.
        // Keep a short pending entry; rendering still waits for the actual eye.
        if (!naturality.util.LoadedChunks.has(level, pos) || !level.getBlockState(pos).is(Blocks.END_PORTAL_FRAME)) return;
        if (ACTIVE.size() >= 128 && !ACTIVE.containsKey(pos)) ACTIVE.remove(ACTIVE.keySet().iterator().next());
        ACTIVE.put(pos.immutable(), new Flash(level.getGameTime()));
    }

    public static float envelope(float age) {
        float t = Math.clamp(age / DURATION_TICKS, 0, 1);
        return 1 - t * t * (3 - 2 * t);
    }

    public static boolean isActive(BlockPos pos) { return ACTIVE.containsKey(pos); }

    private static boolean hasEye(ClientLevel level, BlockPos pos) {
        if (!naturality.util.LoadedChunks.has(level, pos)) return false;
        var state = level.getBlockState(pos);
        return state.is(Blocks.END_PORTAL_FRAME) && state.getValue(EndPortalFrameBlock.HAS_EYE);
    }

    private static void useLevel(@org.jspecify.annotations.Nullable ClientLevel level) {
        if (currentLevel != level) { ACTIVE.clear(); currentLevel = level; }
    }

    public static void tick(Minecraft client) {
        useLevel(client.level);
        if (!naturality.config.NaturalityConfig.get().portalChanges.eyeGlow) { ACTIVE.clear(); return; }
        var currentLevel = EndEyeGlow.currentLevel;
        if (currentLevel == null) return;
        ACTIVE.entrySet().removeIf(entry -> {
            Flash flash = entry.getValue();
            long age = currentLevel.getGameTime() - flash.start;
            if (age >= DURATION_TICKS || !naturality.util.LoadedChunks.has(currentLevel, entry.getKey())
                    || !currentLevel.getBlockState(entry.getKey()).is(Blocks.END_PORTAL_FRAME)) return true;
            if (hasEye(currentLevel, entry.getKey())) { flash.seenEye = true; return false; }
            return flash.seenEye || age >= 3;
        });
    }

    public static void extract(ParticlesRenderState output, Frustum frustum, Camera camera, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        var currentLevel = EndEyeGlow.currentLevel;
        tick(client);
        if (currentLevel == null || ACTIVE.isEmpty()) return;
        EndPortalGlowPalette.prepare(client);
        var state = new GlowState();
        Vec3 eye = camera.position();
        for (var entry : ACTIVE.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.distToCenterSqr(eye) > 64 * 64 || !frustum.isVisible(new AABB(pos).inflate(1))) continue;
            if (!hasEye(currentLevel, pos)) continue;
            float life = envelope(currentLevel.getGameTime() - entry.getValue().start + partialTick);
            if (life <= 0) continue;
            AABB bounds = eyeBounds(client, currentLevel.getBlockState(pos), pos);
            double cx = (bounds.minX + bounds.maxX) * 0.5;
            double cz = (bounds.minZ + bounds.maxZ) * 0.5;
            for (Direction face : Direction.Plane.HORIZONTAL) {
                double x = face == Direction.WEST ? bounds.minX : face == Direction.EAST ? bounds.maxX : cx;
                double z = face == Direction.NORTH ? bounds.minZ : face == Direction.SOUTH ? bounds.maxZ : cz;
                x += pos.getX() + face.getStepX() / 512.0;
                z += pos.getZ() + face.getStepZ() / 512.0;
                if ((eye.x - x) * face.getStepX() + (eye.z - z) * face.getStepZ() >= 0) continue;
                double width = face.getAxis() == Direction.Axis.X ? bounds.getZsize() : bounds.getXsize();
                int tx = -face.getStepZ(), tz = face.getStepX();
                float u = (float) ((x * tx + z * tz - width * 0.5) % 1.5);
                state.strips.add(new Strip(x - eye.x, pos.getY() - eye.y + bounds.minY + 1.0 / 1024,
                    z - eye.z, tx, tz, (float) width, u, life));
            }
        }
        if (!state.isEmpty()) output.add(state);
    }

    /** Locate the raised eye geometry above the unfilled frame's model top. */
    private static AABB eyeBounds(Minecraft client, BlockState filled, BlockPos pos) {
        var empty = quads(client, filled.setValue(EndPortalFrameBlock.HAS_EYE, false), pos);
        float top = 0;
        for (var q : empty) for (int i = 0; i < 4; i++) top = Math.max(top, q.position(i).y());
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (var q : quads(client, filled, pos)) {
            float bottom = Float.POSITIVE_INFINITY, highest = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) { bottom = Math.min(bottom, q.position(i).y()); highest = Math.max(highest, q.position(i).y()); }
            if (bottom < top - 0.0001 || highest <= top + 0.0001) continue;
            for (int i = 0; i < 4; i++) {
                var p = q.position(i);
                minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y()); minZ = Math.min(minZ, p.z());
                maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y()); maxZ = Math.max(maxZ, p.z());
            }
        }
        return Double.isFinite(minX) ? new AABB(minX, minY, minZ, maxX, maxY, maxZ)
            : new AABB(0.25, 13.0 / 16, 0.25, 0.75, 1, 0.75);
    }

    private static List<BakedQuad> quads(Minecraft client, BlockState block, BlockPos pos) {
        var parts = new ArrayList<BlockStateModelPart>();
        client.getModelManager().getBlockStateModelSet().get(block).collectParts(RandomSource.create(block.getSeed(pos)), parts);
        var quads = new ArrayList<BakedQuad>();
        for (var part : parts) {
            quads.addAll(part.getQuads(null));
            for (Direction direction : Direction.values()) quads.addAll(part.getQuads(direction));
        }
        return quads;
    }

    private record Strip(double x, double y, double z, int tx, int tz, float width, float u, float life) { }
    private static final class GlowState extends QuadParticleRenderState {
        private final List<Strip> strips = new ArrayList<>();
        @Override public Set<SingleQuadParticle.Layer> layers() { return Set.of(EndEyeGlowLayer.LAYER); }
        @Override public boolean isEmpty() { return strips.isEmpty(); }
        @Override public void clear() { strips.clear(); }
        @Override public void submit(net.minecraft.client.renderer.SubmitNodeCollector collector,
                net.minecraft.client.renderer.state.level.CameraRenderState camera) {
            if (!isEmpty()) collector.submitQuadParticleGroup(this);
        }
        @Override public void buildLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer) {
            if (layer != EndEyeGlowLayer.LAYER) return;
            for (Strip s : strips) for (int i = 0; i < 4; i++) {
                float along = (i == 0 || i == 1) ? -s.width / 2 + 1f / 512 : s.width / 2 - 1f / 512;
                float height = (i == 1 || i == 2) ? 0.75f * s.life : 0;
                buffer.addVertex((float) (s.x + s.tx * along), (float) (s.y + height), (float) (s.z + s.tz * along))
                    .setUv(s.u + along + s.width / 2, height)
                    .setColor(ARGB.colorFromFloat(s.life, s.life, 1, 1)).setLight(0xF000F0);
            }
        }
    }

    private EndEyeGlow() { }
}
