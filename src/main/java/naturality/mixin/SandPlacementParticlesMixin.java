package naturality.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class SandPlacementParticlesMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void naturality$sandBurst(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND))
                && context.getLevel() instanceof ServerLevel level) {
            // Dedicated placement event broadcasts one outside-face burst to nearby clients.
            level.levelEvent(0x4E5301, context.getClickedPos(), Block.getId(state));
        }
    }
}
