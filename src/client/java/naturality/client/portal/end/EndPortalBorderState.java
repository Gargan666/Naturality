package naturality.client.portal.end;

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

/** Immutable per-frame model snapshots; no persistent world or texture references. */
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
                if (!naturality.util.LoadedChunks.has(level, neighbor)) continue;
                var block = level.getBlockState(neighbor);
                if (block.is(Blocks.END_PORTAL)) {
                    if (Math.abs(neighbor.getX() - cell.getX()) <= 8
                            && Math.abs(neighbor.getZ() - cell.getZ()) <= 8 && !visited.contains(neighbor)) pending.add(neighbor);
                    continue;
                }
                if (block.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) continue;
                Direction face = out.getOpposite();
                if (naturality.config.NaturalityConfig.get().portalChanges.glowEffect && naturality.config.NaturalityConfig.get().portalChanges.endGlow)
                    result.edges.add(new Edge(pos.immutable(), out));
                if (!naturality.config.NaturalityConfig.get().portalChanges.endWalls) continue;
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
            float start = (float) ((x * tx + z * tz - 0.5) % 1.5);
            for (int i = 0; i < 4; i++) {
                float along = (i == 0 || i == 1) ? -0.5f : 0.5f;
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
}
