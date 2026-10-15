package naturality.worldgen.end.lirestonecrags.mixins;

import naturality.worldgen.end.lirestonecrags.LirestoneCrags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ChorusPlantFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChorusPlantFeature.class)
@org.jspecify.annotations.NullMarked
public abstract class ChorusPlantFeatureMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void naturality$avoidCrags(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
            BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
        if(LirestoneCrags.near(level,origin,12))cir.setReturnValue(false);
    }
}
