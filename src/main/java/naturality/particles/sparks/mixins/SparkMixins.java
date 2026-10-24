package naturality.particles.sparks.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.particles.sparks.Sparks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Successful ignition hooks; TNT handles its tool interaction before the item's useOn method. */
@NullMarked
public final class SparkMixins {
    private SparkMixins() {}

    @Mixin(FlintAndSteelItem.class)
    public static abstract class Ignition {
        @Inject(method = "useOn", at = @At("RETURN"))
        private void naturality$sparks(UseOnContext context, CallbackInfoReturnable<InteractionResult> result) {
            if (result.getReturnValue().consumesAction())
                Sparks.ignite(context.getLevel(), context.getClickLocation(), context.getClickedFace());
        }
    }

    @Mixin(TntBlock.class)
    public static abstract class TntIgnition {
        @WrapMethod(method = "useItemOn")
        private InteractionResult naturality$sparks(ItemStack stack, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
            boolean flintAndSteel = stack.is(Items.FLINT_AND_STEEL);
            var result = original.call(stack, state, level, pos, player, hand, hit);
            if (flintAndSteel && result.consumesAction()) Sparks.ignite(level, hit.getLocation(), hit.getDirection());
            return result;
        }
    }
}
