package naturality.fire;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Shared client/server surface geometry, relative to the owning fire block. */
public final class FireSurface {
    private static final float EPSILON = 0.00001F;

    /** A top rectangle with two oriented edges; signs need not align with world axes. */
    public record Patch(float x, float y, float z, float ux, float uz, float vx, float vz) {
        public float mapX(float x, float z) { return this.x + ux * x + vx * z; }
        public float mapZ(float x, float z) { return this.z + uz * x + vz * z; }
        public boolean contains(float px, float pz) {
            float det = ux * vz - uz * vx;
            float a = ((px - x) * vz - (pz - z) * vx) / det;
            float b = (ux * (pz - z) - uz * (px - x)) / det;
            return a >= -EPSILON && a <= 1 + EPSILON && b >= -EPSILON && b <= 1 + EPSILON;
        }
        private boolean axisAligned() {
            return (Math.abs(ux) < EPSILON || Math.abs(uz) < EPSILON)
                && (Math.abs(vx) < EPSILON || Math.abs(vz) < EPSILON);
        }
    }

    private FireSurface() { }

    private static boolean walkThroughFoliage(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()
            && (state.getBlock() instanceof VegetationBlock || state.getBlock() instanceof SugarCaneBlock
                || state.getBlock() instanceof VineBlock || state.getBlock() instanceof GrowingPlantBlock);
    }

    /** Number of plant cells crossed before reaching the physical support. */
    public static int foliageDepth(BlockGetter level, BlockPos firePos) {
        int depth = 0;
        while (depth < 32) {
            var pos = firePos.below(depth + 1);
            if (!walkThroughFoliage(level, pos, level.getBlockState(pos))) break;
            depth++;
        }
        return depth;
    }

    public static boolean isFloorFire(BlockState state) {
        if (!(state.getBlock() instanceof BaseFireBlock)) return false;
        // Side/ceiling-attached flames must stay attached to their own neighbors.
        for (var property : FireBlock.PROPERTY_BY_DIRECTION.values()) {
            if (state.hasProperty(property) && state.getValue(property)) return false;
        }
        return true;
    }

    public static List<Patch> below(BlockGetter level, BlockPos firePos) {
        int foliageDepth = foliageDepth(level, firePos);
        BlockPos pos = firePos.below(foliageDepth + 1);
        BlockState support = level.getBlockState(pos);
        if (support.isAir() || support.getBlock() instanceof BaseFireBlock) return List.of();
        if (support.getBlock() instanceof net.minecraft.world.level.block.StandingSignBlock sign) {
            double angle = Math.toRadians(-sign.getYRotationDegrees(support));
            float ux = (float) Math.cos(angle), uz = (float) -Math.sin(angle);
            float vx = -uz / 12, vz = ux / 12;
            return List.of(new Patch(0.5F - (ux + vx) / 2, 1F / 12 - foliageDepth,
                0.5F - (uz + vz) / 2, ux, uz, vx, vz));
        }
        var tops = new ArrayList<Patch>();
        for (var box : FireGeometry.supportBoxes(level, pos)) {
            tops.add(new Patch((float) box.minX, (float) box.maxY - 1 - foliageDepth, (float) box.minZ,
                (float) (box.maxX - box.minX), 0, 0, (float) (box.maxZ - box.minZ)));
        }
        var ground = exposed(tops);
        if (foliageDepth == 0 || ground.isEmpty()) return ground;
        // Keep the original flame footprint on the plant. Only its base moves
        // to the physical support; using that support's footprint can balloon
        // a small plant fire into a full-block-wide flame.
        var plantTops = new ArrayList<Patch>();
        for (var box : FireGeometry.supportBoxes(level, firePos.below())) {
            plantTops.add(new Patch((float) box.minX, (float) box.maxY - 1, (float) box.minZ,
                (float) (box.maxX - box.minX), 0, 0, (float) (box.maxZ - box.minZ)));
        }
        var result = new ArrayList<Patch>();
        for (var plant : exposed(plantTops)) {
            float centerX = plant.mapX(.5F,.5F), centerZ = plant.mapZ(.5F,.5F);
            for (var base : ground) if (base.contains(centerX, centerZ)) {
                result.add(new Patch(plant.x(), base.y(), plant.z(), plant.ux(), plant.uz(), plant.vx(), plant.vz()));
                break;
            }
        }
        return result.isEmpty() ? ground : List.copyOf(result);
    }

    /** Split at model edges, take the upper envelope, then merge equal-height cells. */
    public static List<Patch> exposed(List<Patch> tops) {
        if (tops.isEmpty()) return List.of();
        if (tops.stream().anyMatch(p -> !p.axisAligned())) {
            // Preserve the exact orientation of rotated boards rather than their oversized AABB.
            return tops.stream().filter(p -> tops.stream().noneMatch(q -> q.y > p.y + EPSILON
                && q.contains(p.x, p.z) && q.contains(p.x + p.ux, p.z + p.uz)
                && q.contains(p.x + p.vx, p.z + p.vz)
                && q.contains(p.x + p.ux + p.vx, p.z + p.uz + p.vz))).distinct().toList();
        }
        var xs = new TreeSet<Float>();
        var zs = new TreeSet<Float>();
        for (Patch p : tops) {
            xs.add(p.x); xs.add(p.x + p.ux + p.vx);
            zs.add(p.z); zs.add(p.z + p.uz + p.vz);
        }
        // Avoid quadratic expansion on pathological resource-pack models.
        if (xs.size() > 128 || zs.size() > 128) return List.of();
        var x = new ArrayList<>(xs);
        var z = new ArrayList<>(zs);
        float[][] heights = new float[x.size() - 1][z.size() - 1];
        for (int i = 0; i < heights.length; i++) {
            for (int j = 0; j < heights[i].length; j++) {
                float h = Float.NEGATIVE_INFINITY;
                for (Patch p : tops) {
                    if (p.contains((x.get(i) + x.get(i + 1)) / 2, (z.get(j) + z.get(j + 1)) / 2)) h = Math.max(h, p.y);
                }
                heights[i][j] = h;
            }
        }
        var result = new ArrayList<Patch>();
        for (int i = 0; i < heights.length; i++) {
            for (int j = 0; j < heights[i].length; j++) {
                float h = heights[i][j];
                if (!Float.isFinite(h)) continue;
                int endX = i + 1, endZ = j + 1;
                while (endX < heights.length && heights[endX][j] == h) endX++;
                outer: while (endZ < heights[i].length) {
                    for (int k = i; k < endX; k++) if (heights[k][endZ] != h) break outer;
                    endZ++;
                }
                result.add(new Patch(x.get(i), h, z.get(j), x.get(endX) - x.get(i), 0, 0, z.get(endZ) - z.get(j)));
                for (int k = i; k < endX; k++) for (int l = j; l < endZ; l++) heights[k][l] = Float.NEGATIVE_INFINITY;
            }
        }
        return List.copyOf(result);
    }
}
