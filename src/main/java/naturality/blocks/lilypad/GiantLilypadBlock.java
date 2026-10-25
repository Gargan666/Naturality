package naturality.blocks.lilypad;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Four vanilla lily-pad surfaces sharing one placement and one item drop. */
@org.jspecify.annotations.NullMarked
public final class GiantLilypadBlock extends LilyPadBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);

    public GiantLilypadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int part = state.getValue(PART);
        // Retain the vanilla outer rim; internal collision edges meet exactly.
        return Block.box((part & 1) == 0 ? 1 : 0, -1, (part & 2) == 0 ? 1 : 0,
            (part & 1) == 0 ? 16 : 15, 0.5, (part & 2) == 0 ? 16 : 15);
    }

    private static BlockPos origin(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.offset(-(part & 1), 0, -(part >> 1));
    }

    private static BlockPos section(BlockPos origin, int part) {
        return origin.offset(part & 1, 0, part >> 1);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        var origin = context.getClickedPos();
        for (int part = 0; part < 4; part++) {
            var pos = section(origin, part);
            if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)
                    || !super.canSurvive(defaultBlockState(), level, pos)) return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        for (int part = 1; part < 4; part++)
            level.setBlock(section(pos, part), state.setValue(PART, part), 3);
    }

    /** Called only for an otherwise valid vanilla lilypad generation attempt. */
    public static boolean generate(net.minecraft.world.level.WorldGenLevel level, BlockPos origin, RandomSource random) {
        var biome = level.getBiome(origin);
        if ((!biome.is(net.minecraft.world.level.biome.Biomes.SWAMP)
                && !biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) || random.nextInt(10) != 0) return false;
        BlockState state = naturality.bootstrap.ContentRegistration.NaturalityBlocks.GIANT_LILYPAD.defaultBlockState();
        for (int part = 0; part < 4; part++) {
            BlockPos pos = section(origin, part);
            if (!level.ensureCanWrite(pos) || !level.isEmptyBlock(pos) || !state.canSurvive(level, pos)) return false;
        }
        state = state.setValue(FACING, Direction.from2DDataValue(random.nextInt(4)));
        for (int part = 0; part < 4; part++) level.setBlock(section(origin, part), state.setValue(PART, part), 2);
        return true;
    }
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        // Defer until all four placement/removal writes have completed.
        ticks.scheduleTick(pos, this, 1);
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos origin = origin(pos, state);
        for (int part = 0; part < 4; part++) {
            BlockPos other = section(origin, part);
            BlockState found = level.getBlockState(other);
            if (!found.is(this) || found.getValue(PART) != part || found.getValue(FACING) != state.getValue(FACING) || !super.canSurvive(found, level, other)) {
                level.destroyBlock(pos, true);
                return;
            }
        }
    }

    private void removeOthers(Level level, BlockPos origin, BlockPos except) {
        for (int part = 0; part < 4; part++) {
            BlockPos other = section(origin, part);
            BlockState found = level.getBlockState(other);
            if (!other.equals(except) && found.is(this) && found.getValue(PART) == part)
                level.setBlock(other, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        removeOthers(level, origin(pos, state), pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

}

