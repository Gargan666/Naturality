package naturality.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class VillagerGatePathMixin extends NodeEvaluator {
    @Inject(method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;", at = @At("RETURN"), cancellable = true)
    private void naturality$gateAsDoor(PathfindingContext context, int x, int y, int z, CallbackInfoReturnable<PathType> cir) {
        if (!(mob instanceof Villager)) return;
        var state = context.getBlockState(new BlockPos(x, y, z));
        // A gate collision reaches into the block above it. Treat that air as
        // the upper half of a door so villagers do not try to jump onto the gate.
        if (state.isAir()) {
            var below = context.getBlockState(new BlockPos(x, y - 1, z));
            if (below.getBlock() instanceof FenceGateBlock) state = below;
        }
        if (state.getBlock() instanceof FenceGateBlock)
            cir.setReturnValue(state.getValue(FenceGateBlock.OPEN) ? PathType.DOOR_OPEN : PathType.DOOR_WOOD_CLOSED);
    }
}
