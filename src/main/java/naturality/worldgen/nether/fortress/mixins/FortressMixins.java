package naturality.worldgen.nether.fortress.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import naturality.worldgen.nether.fortress.FortressBridges;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@NullMarked
public final class FortressMixins {
    private FortressMixins() {}

    @Mixin(NoiseBasedChunkGenerator.class)
    public static abstract class FoundationColumns {
        @WrapOperation(method = "iterateNoiseColumn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler$Bound;sampleVolume(Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;)Lnet/minecraft/world/level/levelgen/densityfunction/ScopedDensityBuffer;"))
        private ScopedDensityBuffer naturality$foundationDensity(DensitySampler.Bound sampler, DensityVolume volume,
                Operation<ScopedDensityBuffer> original) {
            var batched = FortressBridges.foundationDensity((ChunkGenerator)(Object)this, sampler, volume);
            return batched == null ? original.call(sampler, volume) : batched;
        }
    }

    @Mixin(SimpleBlockFeature.class)
    public static abstract class MushroomPlacement {
        @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
        private boolean naturality$avoidFortressBricks(BlockState state, LevelReader level, BlockPos pos,
                Operation<Boolean> original) {
            return !FortressBridges.blocksMushroomGeneration(state, level, pos)
                && original.call(state, level, pos);
        }
    }

    @Mixin(NetherFortressStructure.class)
    public static abstract class BridgeLayout {
        @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
        private void naturality$groundedEntrance(Structure.GenerationContext context,
                CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
            // The chosen ground height must not undergo vanilla's later random shift.
            cir.setReturnValue(FortressBridges.findGenerationPoint(context));
        }
    }
}
