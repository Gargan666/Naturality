package naturality.mixin;

import naturality.worldgen.EndStoneLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.EndIslandFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndIslandFeature.class)
public abstract class SmallEndIslandLayersMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "place")
    private boolean naturality$outerRocks(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
            BlockPos origin, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noise
                && noise.stable(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.END)) {
            // Outer outcrops now belong to the density field: tall, tapered rocks that also
            // participate in height queries and surface layering. Do not add vanilla discs.
            if (Math.hypot(origin.getX(), origin.getZ()) > 1024) return false;
        }
        return original.call(level, generator, random, origin);
    }
    @Inject(method = "place", at = @At("RETURN"))
    private void naturality$smallIsland(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
            BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) EndStoneLayers.smallIsland(level, origin);
    }
}
