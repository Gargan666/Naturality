package naturality.test.mixin;
import naturality.test.BloodMoonSpawnProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NaturalSpawner.class)
public abstract class BloodMoonSpawnProbeMixin {
    @Inject(method="spawnCategoryForChunk",at=@At("HEAD"),cancellable=true)
    private static void probe(MobCategory category,ServerLevel level,LevelChunk chunk,NaturalSpawner.SpawnPredicate predicate,
            NaturalSpawner.AfterSpawnCallback callback,CallbackInfo ci) {
        if(BloodMoonSpawnProbe.enabled) {BloodMoonSpawnProbe.calls++;ci.cancel();}
    }
}
