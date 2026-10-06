package naturality.chorus;

import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ChorusFlowers {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty BLOOM = BooleanProperty.create("bloom");
    private ChorusFlowers() {}

    public static BlockState flower(Direction facing, int age, RandomSource random) {
        return Blocks.CHORUS_FLOWER.defaultBlockState().setValue(FACING, facing)
            .setValue(BLOOM, random.nextInt(10) == 0).setValue(ChorusFlowerBlock.AGE, age);
    }

    public static VoxelShape shape(BlockState state) {
        if (state.getValue(BLOOM)) return Block.box(0, 0, 0, 16, 16, 16);
        return switch (state.getValue(FACING)) {
            case UP -> Block.box(2, 0, 2, 14, 7, 14);
            case DOWN -> Block.box(2, 9, 2, 14, 16, 14);
            case NORTH -> Block.box(2, 2, 9, 14, 14, 16);
            case SOUTH -> Block.box(2, 2, 0, 14, 14, 7);
            case WEST -> Block.box(9, 2, 2, 16, 14, 14);
            case EAST -> Block.box(0, 2, 2, 7, 14, 14);
        };
    }

    public static boolean supported(BlockState state, LevelReader level, BlockPos pos) {
        BlockState support = level.getBlockState(pos.relative(state.getValue(FACING).getOpposite()));
        return support.is(Blocks.CHORUS_PLANT) || support.is(BlockTags.SUPPORTS_CHORUS_FLOWER);
    }

    // Vanilla's ground-oriented rule cannot support sideways or hanging stems.
    // Search only loaded blocks and stop at a bounded distance, never generating chunks.
    public static boolean rooted(LevelReader level, BlockPos start) {
        var visited = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        pending.add(start);
        while (!pending.isEmpty() && visited.size() < 1024) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                var chunk = level.getChunkForCollisions(neighbor.getX() >> 4, neighbor.getZ() >> 4);
                if (chunk == null) continue;
                BlockState state = chunk.getBlockState(neighbor);
                if (state.is(BlockTags.SUPPORTS_CHORUS_PLANT)) return true;
                if (state.is(Blocks.CHORUS_PLANT) && !visited.contains(neighbor)) pending.addLast(neighbor);
            }
        }
        return false;
    }

    private static boolean clear(LevelReader level, BlockPos pos, Direction axis, Direction ignore) {
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != axis.getAxis() && direction != ignore
                    && !level.isEmptyBlock(pos.relative(direction))) return false;
        }
        return true;
    }

    public static void grow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!supported(state, level, pos)) return;
        int age = state.getValue(ChorusFlowerBlock.AGE);
        if (age >= 5) return;
        Direction facing = state.getValue(FACING);
        Direction back = facing.getOpposite();
        BlockPos ahead = pos.relative(facing);
        if (level.isOutsideBuildHeight(ahead) || !level.isEmptyBlock(ahead)) return;
        BlockState support = level.getBlockState(pos.relative(back));
        boolean root = support.is(BlockTags.SUPPORTS_CHORUS_FLOWER);
        boolean forward = root;
        if (support.is(Blocks.CHORUS_PLANT)) {
            int length = 1;
            for (; length < 5; length++) {
                BlockState next = level.getBlockState(pos.relative(back, length + 1));
                if (!next.is(Blocks.CHORUS_PLANT)) {
                    root = next.is(BlockTags.SUPPORTS_CHORUS_FLOWER);
                    break;
                }
            }
            forward = length < 2 || length <= random.nextInt(root ? 5 : 4);
        }
        if (forward && clear(level, ahead, facing, null)
                && !level.isOutsideBuildHeight(pos.relative(facing, 2))
                && level.isEmptyBlock(pos.relative(facing, 2))) {
            level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, Blocks.CHORUS_PLANT.defaultBlockState()), 2);
            place(level, ahead, state.setValue(ChorusFlowerBlock.AGE, age));
            return;
        }
        boolean branched = false;
        if (age < 4) {
            Direction[] perpendicular = java.util.Arrays.stream(Direction.values())
                .filter(d -> d.getAxis() != facing.getAxis()).toArray(Direction[]::new);
            int attempts = random.nextInt(4) + (root ? 1 : 0);
            for (int i = 0; i < attempts; i++) {
                Direction direction = perpendicular[random.nextInt(perpendicular.length)];
                BlockPos target = pos.relative(direction);
                if (!level.isOutsideBuildHeight(target) && level.isEmptyBlock(target)
                        && level.isEmptyBlock(target.relative(back))
                        && clear(level, target, facing, direction.getOpposite())) {
                    place(level, target, state.setValue(FACING, direction).setValue(ChorusFlowerBlock.AGE, age + 1));
                    branched = true;
                }
            }
        }
        if (branched) {
            level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, Blocks.CHORUS_PLANT.defaultBlockState()), 2);
        } else {
            BlockState dead = state.setValue(ChorusFlowerBlock.AGE, 5);
            level.setBlock(pos, dead, 2);
            crown(level, pos.relative(back), dead, random);
            level.levelEvent(1034, pos, 0);
        }
    }

    /** One roll per completed tip; the final stem wears flowers on its free faces. */
    public static boolean crown(LevelAccessor level, BlockPos stem, BlockState flower, RandomSource random) {
        if (!level.getBlockState(stem).is(Blocks.CHORUS_PLANT)) return false;
        int stemNeighbors = 0;
        boolean exposed = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = stem.relative(direction);
            if (level.getBlockState(neighbor).is(Blocks.CHORUS_PLANT)) stemNeighbors++;
            if (!level.isOutsideBuildHeight(neighbor) && level.isEmptyBlock(neighbor)) exposed = true;
        }
        if (stemNeighbors != 1 || !exposed) return false;
        // Four connected stem blocks distinguish a developed plant from a young shoot.
        var visited = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        pending.add(stem);
        while (!pending.isEmpty() && visited.size() < 4) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                var chunk = level.getChunkForCollisions(neighbor.getX() >> 4, neighbor.getZ() >> 4);
                if (chunk != null && chunk.getBlockState(neighbor).is(Blocks.CHORUS_PLANT)
                        && !visited.contains(neighbor)) pending.addLast(neighbor);
            }
        }
        if (visited.size() < 4 || random.nextInt(5) != 0) return false;
        for (Direction direction : Direction.values()) {
            BlockPos target = stem.relative(direction);
            if (!level.isOutsideBuildHeight(target) && level.isEmptyBlock(target)) {
                level.setBlock(target, flower.setValue(FACING, direction).setValue(ChorusFlowerBlock.AGE, 5), 2);
            }
        }
        level.setBlock(stem, ChorusPlantBlock.getStateWithConnections(level, stem, level.getBlockState(stem)), 2);
        return true;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
        level.levelEvent(1033, pos, 0);
    }

    /** Worldgen suppresses some neighbor updates; repair the completed tree as a whole. */
    public static void refreshConnections(LevelAccessor level, BlockPos root) {
        var visited = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        pending.add(root);
        while (!pending.isEmpty() && visited.size() < 1024) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) continue;
            var chunk = level.getChunkForCollisions(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !chunk.getBlockState(pos).is(Blocks.CHORUS_PLANT)) continue;
            BlockState state = chunk.getBlockState(pos);
            BlockState connected = ChorusPlantBlock.getStateWithConnections(level, pos, state);
            if (connected != state) level.setBlock(pos, connected, 18);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                var neighborChunk = level.getChunkForCollisions(neighbor.getX() >> 4, neighbor.getZ() >> 4);
                if (!visited.contains(neighbor) && neighborChunk != null
                        && neighborChunk.getBlockState(neighbor).is(Blocks.CHORUS_PLANT)) pending.addLast(neighbor);
            }
        }
    }
}
