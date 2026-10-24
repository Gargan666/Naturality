package naturality.worldgen.nether.basaltdeltas.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import naturality.worldgen.nether.basaltdeltas.BasaltDeltasFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.DeltaFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

public final class BasaltDeltasMixins {
    @Mixin(DeltaFeature.class)
    public abstract static class BasaltDeltaPoolMixin {
        @Shadow public abstract BlockState contents();

        @ModifyReturnValue(method = "isClear", at = @At("RETURN"))
        private boolean naturality$requireContainingFaces(boolean clear, LevelAccessor level, BlockPos pos) {
            return clear && (!contents().is(Blocks.LAVA) || BasaltDeltasFeature.canContainLava(level, pos));
        }
    }

    private BasaltDeltasMixins() {}
}
