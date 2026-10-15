package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.sky.BloodMoonSpawning;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LocalMobCapCalculator.class)
public abstract class BloodMoonLocalSpawnMixin implements BloodMoonSpawning.State {
    @Unique private int naturality$multiplier = 1;
    public void naturality$spawnMultiplier(int multiplier) { naturality$multiplier = multiplier; }
    @WrapMethod(method="canSpawn")
    private boolean naturality$cap(MobCategory category, ChunkPos pos, Operation<Boolean> original) {
        int previous=BloodMoonSpawning.LOCAL_MULTIPLIER.get();
        BloodMoonSpawning.LOCAL_MULTIPLIER.set(naturality$multiplier);
        try { return original.call(category,pos); }
        finally { BloodMoonSpawning.LOCAL_MULTIPLIER.set(previous); }
    }
}
