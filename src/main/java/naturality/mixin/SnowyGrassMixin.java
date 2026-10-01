package naturality.mixin;

import naturality.snow.SnowGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SnowyBlock.class)
public abstract class SnowyGrassMixin {
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void naturality$covered(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random,
            CallbackInfoReturnable<BlockState> cir) {
        if (!naturality.config.GameplaySettings.snowWrapping(level)) return;
        if (cir.getReturnValue().hasProperty(SnowyBlock.SNOWY))
            cir.setReturnValue(cir.getReturnValue().setValue(SnowyBlock.SNOWY, SnowGeometry.coveredGrass(level, pos)));
    }
}
