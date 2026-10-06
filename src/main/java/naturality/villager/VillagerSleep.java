package naturality.villager;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Persistent sleep debt; recovery sleep overrides the daytime work schedule. */
public final class VillagerSleep {
    public static long debt(Villager body) {
        var state = Reputation.state(body);
        long now = body.level().getOverworldClockTime();
        if (state.lastSleep < 0 || state.lastSleep > now) state.lastSleep = now;
        return now - state.lastSleep;
    }
    public static boolean tick(ServerLevel level, Villager body) {
        var state = Reputation.state(body);
        long now = level.getOverworldClockTime();
        long debt = debt(body);
        if (state.recoveryUntil >= 0 && now >= state.recoveryUntil) {
            if (body.isSleeping()) body.stopSleeping();
            state.recoveryUntil = -1;
            state.lastSleep = now;
            state.seekingBed = false;
            body.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            return false;
        }
        if (body.isSleeping()) {
            state.lastSleep = now;
            return state.recoveryUntil >= 0;
        }
        if (debt < 48000 && state.recoveryUntil < 0) return false;
        if (state.fleeing || body.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
                || body.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)) return false;
        if (body.getTradingPlayer() instanceof ServerPlayer player) player.closeContainer();
        if (!state.seekingBed) {
            body.getBrain().stopAll(level, body);
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            body.getNavigation().stop();
            state.seekingBed = true;
        }
        body.getBrain().setActiveActivityIfPossible(Activity.REST);
        if (state.sleepBed == null || !available(level, state.sleepBed)) state.sleepBed = null;
        if (state.sleepBed == null && (state.bedSearchTick < 0 || body.tickCount - state.bedSearchTick >= 100)) {
            state.bedSearchTick = body.tickCount;
            var center = body.blockPosition();
            state.sleepBed = BlockPos.betweenClosedStream(center.offset(-32, -8, -32), center.offset(32, 8, 32))
                .filter(pos -> naturality.util.LoadedChunks.has(level, pos) && available(level, pos))
                .map(BlockPos::immutable).sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .filter(pos -> { var path = body.getNavigation().createPath(pos, 1); return path != null && path.canReach(); })
                .findFirst().orElse(null);
            if (state.sleepBed == null) {
                var roam = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(body, 16, 7);
                if (roam != null) body.getNavigation().moveTo(roam.x, roam.y, roam.z, .6);
            }
        }
        if (state.sleepBed != null) {
            if (body.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(state.sleepBed)) < 4) {
                if (body.startSleeping(state.sleepBed)) {
                    state.recoveryUntil = (Math.floorDiv(now, 24000) + 1) * 24000;
                    state.lastSleep = now;
                    body.getNavigation().stop();
                }
            } else if (body.getNavigation().isDone() || body.tickCount % 20 == 0)
                body.getNavigation().moveTo(state.sleepBed.getX() + .5, state.sleepBed.getY(), state.sleepBed.getZ() + .5, .6);
        }
        return true;
    }
    private static boolean available(ServerLevel level, BlockPos pos) {
        var block = level.getBlockState(pos);
        return block.getBlock() instanceof BedBlock && block.getValue(BedBlock.PART) == BedPart.HEAD && !block.getValue(BedBlock.OCCUPIED);
    }
    private VillagerSleep() {}
}
