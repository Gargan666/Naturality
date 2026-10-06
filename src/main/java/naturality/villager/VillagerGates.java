package naturality.villager;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import naturality.mixin.FenceGateTypeAccess;
import naturality.mixin.DoorInteractionAccess;

/** Fence gates participate in the same path-node and shared closing memory as doors. */
public final class VillagerGates {
    private VillagerGates() { }
    public static BehaviorControl<Villager> create() {
        return new Behavior<>(ImmutableMap.of(), 1) {
            @Override protected boolean timedOut(long time) { return false; }
            @Override protected boolean canStillUse(ServerLevel level, Villager body, long time) {
                return body.isAlive();
            }
            @Override protected void tick(ServerLevel level, Villager body, long time) {
                var path = body.getBrain().getMemory(MemoryModuleType.PATH).orElse(body.getNavigation().getPath());
                var remembered = body.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE);
                Set<GlobalPos> doors = remembered.orElseGet(HashSet::new);
                Node from = path == null || path.notStarted() ? null : path.getPreviousNode();
                Node to = path == null || path.isDone() ? null : path.getNextNode();
                if (path != null && !path.notStarted() && !path.isDone()) {
                    boolean touched = open(level, body, from.asBlockPos(), true, doors);
                    touched |= open(level, body, to.asBlockPos(), false, doors);
                    if (touched) body.getBrain().setMemory(MemoryModuleType.DOORS_TO_CLOSE, doors);
                }
                close(level, body, from, to, doors, body.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES));
            }
        };
    }
    private static boolean open(ServerLevel level, LivingEntity body, BlockPos pos, boolean passed, Set<GlobalPos> doors) {
        var state = level.getBlockState(pos);
        if (state.isAir() && level.getBlockState(pos.below()).getBlock() instanceof FenceGateBlock) {
            pos = pos.below(); state = level.getBlockState(pos);
        }
        if (!(state.getBlock() instanceof FenceGateBlock)) return false;
        boolean wasOpen = state.getValue(FenceGateBlock.OPEN);
        if (!wasOpen) setOpen(level, body, pos, state, true);
        // Match doors: remember the previous node even if it was already open;
        // only take responsibility for the next node when this villager opens it.
        if (passed || !wasOpen) doors.add(GlobalPos.of(level.dimension(), pos));
        return true;
    }
    private static void setOpen(ServerLevel level, LivingEntity body, BlockPos pos, BlockState state, boolean open) {
        if (open && state.getValue(FenceGateBlock.FACING) == body.getDirection().getOpposite())
            state = state.setValue(FenceGateBlock.FACING, body.getDirection());
        var wood = ((FenceGateTypeAccess)state.getBlock()).naturality$woodType();
        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, open), 10);
        level.playSound(null, pos, open ? wood.fenceGateOpen() : wood.fenceGateClose(), SoundSource.BLOCKS,
            1F, level.getRandom().nextFloat() * .1F + .9F);
        level.gameEvent(body, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }
    private static boolean onGate(Node node, BlockPos pos) {
        return node != null && (node.asBlockPos().equals(pos) || node.asBlockPos().equals(pos.above()));
    }
    public static void close(ServerLevel level, LivingEntity body, Node from, Node to,
            Set<GlobalPos> doors, Optional<List<LivingEntity>> neighbors) {
        var iterator = doors.iterator();
        while (iterator.hasNext()) {
            var global = iterator.next();
            var pos = global.pos();
            if (global.dimension() != level.dimension()) continue;
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FenceGateBlock)) continue;
            if (onGate(from, pos) || onGate(to, pos)) continue;
            // Path indices can advance before the villager has physically cleared the opening.
            if (body.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos).expandTowards(0, .5, 0))) continue;
            if (!pos.closerToCenterThan(body.position(), 3) || !state.getValue(FenceGateBlock.OPEN)
                    || state.getValue(FenceGateBlock.POWERED)
                    || DoorInteractionAccess.naturality$othersComing(body, pos, neighbors)
                    || DoorInteractionAccess.naturality$othersComing(body, pos.above(), neighbors)) {
                iterator.remove();
                continue;
            }
            setOpen(level, body, pos, state, false);
            iterator.remove();
        }
    }
}
