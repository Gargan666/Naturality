package naturality.blocks.clover;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

/** Rare clovers retain flower-bed placement and drops but cannot be multiplied with bonemeal. */
@NullMarked
public final class FourLeafCloversBlock extends FlowerBedBlock {
    public FourLeafCloversBlock(Properties properties) {
        super(properties, 3);
    }

    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return false;
    }

    @Override public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return false;
    }

    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        // Also prevent growth or item drops if a caller bypasses the eligibility check.
    }
}
