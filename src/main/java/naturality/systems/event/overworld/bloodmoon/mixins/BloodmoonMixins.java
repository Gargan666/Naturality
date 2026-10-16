package naturality.systems.event.overworld.bloodmoon.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.systems.event.overworld.bloodmoon.BloodMoonCycle.BloodMoonSpawning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class BloodmoonMixins {
    private BloodmoonMixins() {}

    @Mixin(targets="net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
    @org.jspecify.annotations.NullMarked
    public static abstract class BloodMoonLocalCountMixin {
        @ModifyExpressionValue(method="canSpawn", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
        private int naturality$cap(int original) { return original * BloodMoonSpawning.LOCAL_MULTIPLIER.get(); }
    }

    @Mixin(LocalMobCapCalculator.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BloodMoonLocalSpawnMixin implements BloodMoonSpawning.State {
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

    @Mixin(NaturalSpawner.SpawnState.class)
    @org.jspecify.annotations.NullMarked
    public interface BloodMoonSpawnAccess {
        @Invoker("canSpawnForCategoryLocal") boolean naturality$canSpawnLocal(MobCategory category, ChunkPos pos);
    }

    @Mixin(NaturalSpawner.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BloodMoonSpawnMixin {
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

    @Mixin(NaturalSpawner.SpawnState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BloodMoonSpawnStateMixin implements BloodMoonSpawning.State {
        @Unique private int naturality$multiplier = 1;
        public void naturality$spawnMultiplier(int multiplier) { naturality$multiplier = multiplier; }
        @ModifyExpressionValue(method="canSpawnForCategoryGlobal", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
        private int naturality$cap(int original) { return original * naturality$multiplier; }
    }
}
