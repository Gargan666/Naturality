package naturality.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A workstation is an interaction target, never a footstep in its owner's path. */
@Mixin(WalkNodeEvaluator.class)
public abstract class VillagerStationPathMixin {
    @Inject(method = "getPathTypeOfMob", at = @At("HEAD"), cancellable = true)
    private void naturality$avoidOwnStation(PathfindingContext context, int x, int y, int z, Mob mob,
            CallbackInfoReturnable<PathType> cir) {
        if (!(mob instanceof Villager villager)) return;
        var job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (job.isPresent() && job.get().dimension().equals(villager.level().dimension())) {
            var pos = job.get().pos();
            if (pos.getX() == x && pos.getY() + 1 == y && pos.getZ() == z)
                cir.setReturnValue(PathType.BLOCKED);
        }
    }
}
