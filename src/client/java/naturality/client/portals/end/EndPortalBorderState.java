package naturality.client.portals.end;

import naturality.client.portals.end.EndPortalBorderLayers.EndPortalGlowPalette;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import naturality.client.portals.end.EndPortalBorderLayers.EndEyeGlowLayer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Immutable per-frame model snapshots; no persistent world or texture references. */
@org.jspecify.annotations.NullMarked
public final class EndPortalBorderState {
    private record Wall(BakedQuad quad, BlockPos pos, int color, int light) { }
    private record Edge(BlockPos pos, Direction outward) { }
    private final List<Wall> walls = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final BlockPos cell;
    private final Vec3 eye;
    private final int cellX, cellZ;

    private EndPortalBorderState(BlockPos cell, Vec3 eye) {
        this.cell = cell.immutable();
        this.eye = eye;
        cellX = cell.getX() - (int) Math.floor(eye.x);
        cellZ = cell.getZ() - (int) Math.floor(eye.z);
    }

    public static @org.jspecify.annotations.Nullable EndPortalBorderState extract(TheEndPortalBlockEntity entity, Vec3 eye) {
        var level = entity.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
        BlockPos cell = entity.getBlockPos();
        if (level == null || !entity.getBlockState().is(Blocks.END_PORTAL)
                || eye.y <= cell.getY() + 0.75 || cell.distToCenterSqr(eye) > 64 * 64) return null;
        var result = new EndPortalBorderState(cell, eye);
        var client = Minecraft.getInstance();
        EndPortalGlowPalette.prepare(client);
        var pending = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var parts = new ArrayList<BlockStateModelPart>();
        var random = RandomSource.create(0);
        pending.add(cell);
        // Each tile clips projected walls to its own portal footprint. Gathering
        // connected borders avoids seams when a wall projects across tile edges.
        while (!pending.isEmpty() && visited.size() < 256) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) continue;
            for (Direction out : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.relative(out);
                if (!naturality.api.worlds.LoadedChunks.has(level, neighbor)) continue;
                var block = level.getBlockState(neighbor);
                if (block.is(Blocks.END_PORTAL)) {
                    if (Math.abs(neighbor.getX() - cell.getX()) <= 8
                            && Math.abs(neighbor.getZ() - cell.getZ()) <= 8 && !visited.contains(neighbor)) pending.add(neighbor);
                    continue;
                }
                if (block.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) continue;
                Direction face = out.getOpposite();
                if (naturality.config.live.NaturalityConfig.get().portalChanges.glowEffect && naturality.config.live.NaturalityConfig.get().portalChanges.endGlow)
                    result.edges.add(new Edge(pos.immutable(), out));
                if (!naturality.config.live.NaturalityConfig.get().portalChanges.endWalls) continue;
                // Only faces looking into the opening can contribute a wall.
                if ((eye.x - (pos.getX() + 0.5 + out.getStepX() * 0.5)) * face.getStepX()
                        + (eye.z - (pos.getZ() + 0.5 + out.getStepZ() * 0.5)) * face.getStepZ() <= 0) continue;
                parts.clear();
                random.setSeed(block.getSeed(neighbor));
                client.getModelManager().getBlockStateModelSet().get(block).collectParts(random, parts);
                for (var part : parts) {
                    var quads = new ArrayList<>(part.getQuads(face));
                    for (var quad : part.getQuads(null)) if (quad.direction() == face) quads.add(quad);
                    for (var quad : quads) {
                        // Select boundary faces, not inset parts such as the eye on a frame.
                        boolean boundary = true;
                        for (int i = 0; i < 4; i++) {
                            var p = quad.position(i);
                            float coordinate = face.getAxis() == Direction.Axis.X ? p.x() : p.z();
                            float expected = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
                            if (Math.abs(coordinate - expected) > 0.001) boundary = false;
                        }
                        if (!boundary) continue;
                        var tintSource = quad.materialInfo().isTinted()
                            ? client.getBlockColors().getTintSource(block, quad.materialInfo().tintIndex()) : null;
                        int tint = tintSource != null ? tintSource.colorInWorld(block, level, neighbor) : 0xFFFFFF;
                        float shade = level.cardinalLighting().byFace(face);
                        int color = ARGB.color(255, (int) (ARGB.red(tint) * shade),
                            (int) (ARGB.green(tint) * shade), (int) (ARGB.blue(tint) * shade));
                        int packedLight = Math.max(block.getLightEmission(), level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos))
                            | (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) << 4);
                        result.walls.add(new Wall(quad, neighbor.immutable(), color, packedLight));
                    }
                }
            }
        }
        return result;
    }

    public void submit(SubmitNodeCollector collector) {
        // Vertices below are already camera-relative, so use an identity pose.
        // One render type lets regular transparency sort walls and glow together.
        if (!walls.isEmpty() || !edges.isEmpty()) collector.submitCustomGeometry(new PoseStack(), EndPortalBorderLayers.BORDER,
            (_, buffer) -> { buildWalls(buffer); buildGlow(buffer); });
    }

    private void buildWalls(VertexConsumer buffer) {
        for (Wall wall : walls) {
            for (int i = 0; i < 4; i++) {
                var p = wall.quad.position(i);
                var normal = wall.quad.direction();
                long uv = wall.quad.packedUV(i);
                // Subtract the camera before adding model fractions; preserve
                // the physical inset even at large world coordinates.
                buffer.addVertex((float) ((wall.pos.getX() - eye.x) + p.x() + normal.getStepX() / 1024.0),
                    (float) ((wall.pos.getY() - eye.y) + p.y()),
                    (float) ((wall.pos.getZ() - eye.z) + p.z() + normal.getStepZ() / 1024.0))
                    .setColor(wall.color).setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
                    .setOverlay(cellX & 65535 | (cellZ & 65535) << 16)
                    .setLight(Math.round(p.y() * 4096) & 65535 | wall.light << 16)
                    .setNormal(0, 1, 0);
            }
        }
    }

    private void buildGlow(VertexConsumer buffer) {
        for (Edge edge : edges) {
            Direction out = edge.outward;
            // A second inward step keeps the glow ahead of the textured wall.
            double x = edge.pos.getX() + 0.5 + out.getStepX() * (0.5 - 1.0 / 512);
            double z = edge.pos.getZ() + 0.5 + out.getStepZ() * (0.5 - 1.0 / 512);
            if ((eye.x - x) * -out.getStepX() + (eye.z - z) * -out.getStepZ() <= 0) continue;
            int tx = out.getStepZ(), tz = -out.getStepX();
            Direction end = tx > 0 ? Direction.EAST : tx < 0 ? Direction.WEST : tz > 0 ? Direction.SOUTH : Direction.NORTH;
            // Meet the perpendicular inset plane at the corner instead of
            // letting both translucent strips continue through one another.
            float lower = edges.contains(new Edge(edge.pos, end.getOpposite())) ? -0.5f + 1f / 512 : -0.5f;
            float upper = edges.contains(new Edge(edge.pos, end)) ? 0.5f - 1f / 512 : 0.5f;
            float start = (float) ((x * tx + z * tz - 0.5) % 1.5);
            for (int i = 0; i < 4; i++) {
                float along = (i == 0 || i == 1) ? lower : upper;
                float height = (i == 1 || i == 2) ? 1.75f : 0;
                buffer.addVertex((float) (x + tx * along - eye.x),
                    (float) ((cell.getY() - eye.y) + height), (float) (z + tz * along - eye.z))
                    .setUv(start + along + 0.5f, height).setColor(0xBFFFFFFF)
                    .setOverlay(cellX & 65535 | (cellZ & 65535) << 16)
                    .setLight(Math.round(height * 4096) & 65535 | 255 << 16)
                    .setNormal(edge.pos.equals(cell) ? 1 : 0, 0, 0);
            }
        }
    }

    public interface EndPortalBorderAccess {
        @org.jspecify.annotations.Nullable EndPortalBorderState naturality$getBorder();
        void naturality$setBorder(@org.jspecify.annotations.Nullable EndPortalBorderState state);
    }

    /** Only vanilla insertion events create entries; existing eyes never auto-start. */
    public static final class EndEyeGlow {
        public static final int DURATION_TICKS = naturality.client.api.rendering.PlacementGlow.DURATION_TICKS;
        private static final class Flash {
            final long start;
            boolean seenEye;
            Flash(long start) { this.start = start; }
        }
        private static final Map<BlockPos, Flash> ACTIVE = new LinkedHashMap<>();
        private static @org.jspecify.annotations.Nullable ClientLevel currentLevel;

        public static void start(ClientLevel level, BlockPos pos) {
            if (!naturality.config.live.NaturalityConfig.get().portalChanges.eyeGlow) return;
            useLevel(level);
            // The level-event packet can precede the chunk's block-state update.
            // Keep a short pending entry; rendering still waits for the actual eye.
            if (!naturality.api.worlds.LoadedChunks.has(level, pos) || !level.getBlockState(pos).is(Blocks.END_PORTAL_FRAME)) return;
            if (ACTIVE.size() >= 128 && !ACTIVE.containsKey(pos)) ACTIVE.remove(ACTIVE.keySet().iterator().next());
            ACTIVE.put(pos.immutable(), new Flash(level.getGameTime()));
        }

        public static float envelope(float age) {
            return naturality.client.api.rendering.PlacementGlow.envelope(age);
        }

        public static boolean isActive(BlockPos pos) { return ACTIVE.containsKey(pos); }

        private static boolean hasEye(ClientLevel level, BlockPos pos) {
            if (!naturality.api.worlds.LoadedChunks.has(level, pos)) return false;
            var state = level.getBlockState(pos);
            return state.is(Blocks.END_PORTAL_FRAME) && state.getValue(EndPortalFrameBlock.HAS_EYE);
        }

        private static void useLevel(@org.jspecify.annotations.Nullable ClientLevel level) {
            if (currentLevel != level) { ACTIVE.clear(); currentLevel = level; }
        }

        public static void tick(Minecraft client) {
            useLevel(client.level);
            if (!naturality.config.live.NaturalityConfig.get().portalChanges.eyeGlow) { ACTIVE.clear(); return; }
            var currentLevel = EndEyeGlow.currentLevel;
            if (currentLevel == null) return;
            ACTIVE.entrySet().removeIf(entry -> {
                Flash flash = entry.getValue();
                long age = currentLevel.getGameTime() - flash.start;
                if (age >= DURATION_TICKS || !naturality.api.worlds.LoadedChunks.has(currentLevel, entry.getKey())
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
}
