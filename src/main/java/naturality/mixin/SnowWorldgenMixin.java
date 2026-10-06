package naturality.mixin;

import naturality.snow.SnowWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SnowAndFreezeFeature.class)
public abstract class SnowWorldgenMixin {
    @Inject(method="place",at=@At("RETURN"))
    private void naturality$snowOverPlants(WorldGenLevel level,ChunkGenerator generator,RandomSource random,
            BlockPos origin,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValueZ())SnowWorldgen.placeOnFoliage(level,origin);
    }
}
