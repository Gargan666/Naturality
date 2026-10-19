package naturality.blocks.ash.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.blocks.ash.Ash;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Ash owns its plant-preserving placement policy independently of the snow setting. */
public final class AshMixins {
    private AshMixins() {}

    @Mixin(BlockItem.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class AshPlacementMixin {
        @Shadow public abstract net.minecraft.world.level.block.Block getBlock();

        @WrapMethod(method = "place")
        private InteractionResult naturality$ashPlacement(BlockPlaceContext context, Operation<InteractionResult> original) {
            return original.call(getBlock() instanceof Ash.Layer ? Ash.placementContext(context) : context);
        }
    }
}
