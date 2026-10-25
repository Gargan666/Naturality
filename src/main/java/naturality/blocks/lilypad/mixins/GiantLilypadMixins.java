package naturality.blocks.lilypad.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.blocks.lilypad.GiantLilypadBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Integration with vanilla lilypad generation; no additional placement passes. */
public final class GiantLilypadMixins {
    private GiantLilypadMixins() {}

    @Mixin(SimpleBlockFeature.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GiantLilypadGenerationMixin {
        @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
        private boolean naturality$replaceLilypad(WorldGenLevel target, BlockPos pos, BlockState state, int flags,
                Operation<Boolean> original, WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            if (state.is(Blocks.LILY_PAD) && GiantLilypadBlock.generate(level, pos, random)) return true;
            return original.call(target, pos, state, flags);
        }
    }
}
