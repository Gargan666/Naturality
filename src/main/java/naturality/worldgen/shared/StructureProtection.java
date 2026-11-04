package naturality.worldgen.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilStructure;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Shared protection for decorations and fossils, independent of a structure's palette. */
@NullMarked
public final class StructureProtection {
    private static final ThreadLocal<@Nullable Scope> ACTIVE = new ThreadLocal<>();

    private StructureProtection() {}

    public static boolean isNetherBrick(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return (id.getNamespace().equals("minecraft") || id.getNamespace().equals("naturality"))
            && id.getPath().contains("nether_brick");
    }

    public static boolean decorate(WorldGenLevel level, Supplier<Boolean> placement) {
        if (!(level instanceof WorldGenRegion region)) return placement.get();
        Scope previous = ACTIVE.get();
        if (previous != null && previous.region == region) return placement.get();
        ACTIVE.set(new Scope(region));
        try {
            return placement.get();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static boolean blocksWrite(WorldGenRegion region, BlockPos pos, BlockState state) {
        Scope scope = ACTIVE.get();
        if (scope == null || scope.region != region || !region.isWithinWriteZone(pos)) return false;
        // Ash's own indoor/roof checks intentionally allow exposed structure ledges.
        if (state.is(NaturalityBlocks.ASH) && region.getBlockState(pos).isAir()) return false;
        if (isNetherBrick(region.getBlockState(pos)) || isNetherBrick(region.getBlockState(pos.below()))) return true;
        for (BoundingBox box : scope.boxes(pos.getX() >> 4, pos.getZ() >> 4)) {
            if (box.isInside(pos) || box.isInside(pos.below())) return true;
        }
        return false;
    }

    public static boolean overlapsStructure(WorldGenLevel level, BoundingBox fossil) {
        if (!(level instanceof WorldGenRegion region)) return false;
        Scope scope = new Scope(region);
        BoundingBox occupied = new BoundingBox(fossil.minX(), fossil.minY() - 1, fossil.minZ(),
            fossil.maxX(), fossil.maxY(), fossil.maxZ());
        for (int x = fossil.minX() >> 4; x <= fossil.maxX() >> 4; x++) {
            for (int z = fossil.minZ() >> 4; z <= fossil.maxZ() >> 4; z++) {
                // Never request chunks outside the generation dependency region.
                if (!region.hasChunk(x, z)) return true;
                for (BoundingBox box : scope.boxes(x, z)) {
                    if (box.intersects(occupied)) return true;
                }
            }
        }
        return false;
    }

    private static final class Scope {
        final WorldGenRegion region;
        final Map<Long, List<BoundingBox>> byChunk = new HashMap<>();

        Scope(WorldGenRegion region) { this.region = region; }

        List<BoundingBox> boxes(int x, int z) {
            long key = ((long)x << 32) ^ (z & 0xffffffffL);
            return byChunk.computeIfAbsent(key, _ -> {
                List<BoundingBox> boxes = new ArrayList<>();
                var references = region.getChunk(x, z, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
                for (var entry : references.entrySet()) {
                    if (entry.getKey() instanceof NetherFossilStructure) continue;
                    for (long packed : entry.getValue()) {
                        ChunkPos startPos = ChunkPos.unpack(packed);
                        if (!region.hasChunk(startPos.x(), startPos.z())) {
                            // A referenced structure beyond this task's cache must not trigger generation.
                            boxes.add(new BoundingBox(x << 4, region.getMinY(), z << 4,
                                (x << 4) + 15, region.getMaxY(), (z << 4) + 15));
                            continue;
                        }
                        var start = region.getChunk(startPos.x(), startPos.z(), ChunkStatus.STRUCTURE_STARTS)
                            .getStartForStructure(entry.getKey());
                        if (start != null && start.isValid()) {
                            for (var piece : start.getPieces()) boxes.add(piece.getBoundingBox());
                        }
                    }
                }
                return boxes;
            });
        }
    }
}
