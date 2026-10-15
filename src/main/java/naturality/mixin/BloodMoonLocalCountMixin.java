package naturality.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.sky.BloodMoonSpawning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public abstract class BloodMoonLocalCountMixin {
    @ModifyExpressionValue(method="canSpawn", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int naturality$cap(int original) { return original * BloodMoonSpawning.LOCAL_MULTIPLIER.get(); }
}
