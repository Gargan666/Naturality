package naturality.systems.villagerwork.gates.mixins;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;
import naturality.systems.villagerwork.gates.VillagerGates;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Node;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class GatesMixins {
    private GatesMixins() {}

    @Mixin(InteractWithDoor.class)
    @org.jspecify.annotations.NullMarked
    public interface DoorInteractionAccess {
        @Invoker("areOtherMobsComingThroughDoor")
        static boolean naturality$othersComing(LivingEntity body, BlockPos pos, Optional<List<LivingEntity>> neighbors) {
            throw new AssertionError();
        }
    }

    @Mixin(FenceGateBlock.class)
    @org.jspecify.annotations.NullMarked
    public interface FenceGateTypeAccess {
        @Accessor("type") WoodType naturality$woodType();
    }

    @Mixin(InteractWithDoor.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerGateCloseMixin {
        @Inject(method = "closeDoorsThatIHaveOpenedOrPassedThrough", at = @At("HEAD"), cancellable = true)
        private static void naturality$closeGates(ServerLevel level, LivingEntity body, Node from, Node to,
                Set<GlobalPos> doors, Optional<List<LivingEntity>> neighbors, CallbackInfo ci) {
            if (!(body instanceof Villager)) return;
            var gates = new java.util.HashSet<GlobalPos>();
            doors.removeIf(global -> {
                if (global.dimension() == level.dimension()
                        && level.getBlockState(global.pos()).getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
                    gates.add(global); return true;
                }
                return false;
            });
            if (gates.isEmpty()) return;
            VillagerGates.close(level, body, from, to, gates, neighbors);
            // Vanilla processes only actual DoorBlocks; preserve gates still in use.
            InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, body, from, to, doors, neighbors);
            doors.addAll(gates);
            ci.cancel();
        }
    }

    @Mixin(WalkNodeEvaluator.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerGatePathMixin extends NodeEvaluator {
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
}
