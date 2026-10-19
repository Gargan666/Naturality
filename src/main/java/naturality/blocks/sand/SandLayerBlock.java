package naturality.blocks.sand;

import naturality.api.blocks.FallingLayerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Flat, waterloggable sand layers with collision matching their visible height. */
@org.jspecify.annotations.NullMarked
public final class SandLayerBlock extends FallingLayerBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
    public SandLayerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }
}
