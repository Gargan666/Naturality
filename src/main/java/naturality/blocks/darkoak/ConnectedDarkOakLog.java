package naturality.blocks.darkoak;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.NullMarked;

/** Saved cross-section membership: cutting a trunk reveals wood instead of restoring bark. */
@NullMarked
public final class ConnectedDarkOakLog extends RotatedPillarBlock {
    public static final IntegerProperty QUARTER = IntegerProperty.create("quarter", 0, 4);

    public ConnectedDarkOakLog(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(QUARTER, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(QUARTER);
    }

    // Coordinates in the plane perpendicular to the trunk, also used by the model UVs.
    public static Direction first(Direction.Axis axis) { return axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST; }
    public static Direction second(Direction.Axis axis) { return axis == Direction.Axis.Y ? Direction.SOUTH : Direction.UP; }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (state.getValue(QUARTER) == 0) level.scheduleTick(pos, this, 1);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (state.getValue(QUARTER) == 0 && neighbor.is(this)) ticks.scheduleTick(pos, this, 1);
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(QUARTER) != 0) return;
        Direction.Axis axis = state.getValue(AXIS);
        Direction u = first(axis), v = second(axis);
        boolean waitingForChunk = false;
        for (int du = -1; du <= 0; du++) for (int dv = -1; dv <= 0; dv++) {
            BlockPos origin = pos.relative(u, du).relative(v, dv);
            boolean matches = true;
            for (int a = 0; a < 2 && matches; a++) for (int b = 0; b < 2; b++) {
                BlockPos member = origin.relative(u, a).relative(v, b);
                if (!naturality.api.worlds.LoadedChunks.has(level, member)) {
                    waitingForChunk = true;
                    matches = false;
                    break;
                }
                BlockState other = level.getBlockState(member);
                int expected = 1 + a + 2 * b;
                if (!other.is(this) || other.getValue(AXIS) != axis
                        || (other.getValue(QUARTER) != 0 && other.getValue(QUARTER) != expected)) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
                BlockPos member = origin.relative(u, a).relative(v, b);
                level.setBlock(member, state.setValue(QUARTER, 1 + a + 2 * b), Block.UPDATE_CLIENTS);
            }
            return;
        }
        // Retry border groups without loading their neighbors or waiting on chunk futures.
        if (waitingForChunk) level.scheduleTick(pos, this, 20);
    }

    private static BlockState transformed(BlockState state, Direction u, Direction v, Direction.Axis axis) {
        int old = state.getValue(QUARTER);
        if (old == 0) return state.setValue(AXIS, axis);
        // Transform inward-facing directions, rather than assigning world-aligned quarters anew.
        Direction inwardU = ((old - 1) & 1) == 0 ? u : u.getOpposite();
        Direction inwardV = ((old - 1) & 2) == 0 ? v : v.getOpposite();
        int a = inwardU == first(axis).getOpposite() || inwardV == first(axis).getOpposite() ? 1 : 0;
        int b = inwardU == second(axis).getOpposite() || inwardV == second(axis).getOpposite() ? 1 : 0;
        return state.setValue(AXIS, axis).setValue(QUARTER, 1 + a + 2 * b);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(AXIS);
        Direction.Axis rotated = rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, axis)).getAxis();
        return transformed(state, rotation.rotate(first(axis)), rotation.rotate(second(axis)), rotated);
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction.Axis axis = state.getValue(AXIS);
        return transformed(state, mirror.mirror(first(axis)), mirror.mirror(second(axis)), axis);
    }

    public static void initialize() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, _) -> {
            // Read only the supplied chunk here: generation and existing trees join on scheduled ticks.
            var sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                var section = sections[i];
                if (!section.maybeHas(s -> s.getBlock() instanceof ConnectedDarkOakLog && s.getValue(QUARTER) == 0)) continue;
                int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (state.getBlock() instanceof ConnectedDarkOakLog && state.getValue(QUARTER) == 0) {
                        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                        level.scheduleTick(pos, state.getBlock(), 1);
                    }
                }
            }
        });
    }
}
