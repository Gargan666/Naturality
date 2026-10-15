package naturality.mixin;

import java.util.List;
import naturality.sky.BloodMoonSpawning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
public abstract class BloodMoonSpawnMixin {
    @Inject(method="createState", at=@At("RETURN"))
    private static void naturality$state(int chunks, ServerLevel level, NaturalSpawner.ChunkGetter getter,
            LocalMobCapCalculator local, CallbackInfoReturnable<NaturalSpawner.SpawnState> cir) {
        int multiplier=BloodMoonSpawning.multiplier(level);
        ((BloodMoonSpawning.State)cir.getReturnValue()).naturality$spawnMultiplier(multiplier);
        ((BloodMoonSpawning.State)local).naturality$spawnMultiplier(multiplier);
    }
    @Redirect(method="spawnForChunk", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/NaturalSpawner;spawnCategoryForChunk(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V"))
    private static void naturality$attempts(MobCategory category, ServerLevel level, LevelChunk chunk,
            NaturalSpawner.SpawnPredicate predicate, NaturalSpawner.AfterSpawnCallback callback,
            ServerLevel outerLevel, LevelChunk outerChunk, NaturalSpawner.SpawnState state, List<MobCategory> categories) {
        NaturalSpawner.spawnCategoryForChunk(category,level,chunk,predicate,callback);
        if (BloodMoonSpawning.multiplier(level)==2 && ((BloodMoonSpawnAccess)state).naturality$canSpawnLocal(category,chunk.getPos()))
            NaturalSpawner.spawnCategoryForChunk(category,level,chunk,predicate,callback);
    }
}
