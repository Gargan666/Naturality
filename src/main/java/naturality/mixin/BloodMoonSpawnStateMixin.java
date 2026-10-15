package naturality.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.sky.BloodMoonSpawning;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;


@Mixin(NaturalSpawner.SpawnState.class)
public abstract class BloodMoonSpawnStateMixin implements BloodMoonSpawning.State {
    @Unique private int naturality$multiplier = 1;
    public void naturality$spawnMultiplier(int multiplier) { naturality$multiplier = multiplier; }
    @ModifyExpressionValue(method="canSpawnForCategoryGlobal", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int naturality$cap(int original) { return original * naturality$multiplier; }
}
