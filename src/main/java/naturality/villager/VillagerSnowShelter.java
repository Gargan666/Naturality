package naturality.villager;

import java.util.ArrayList;
import java.util.Comparator;
import naturality.util.LoadedChunks;
import naturality.weather.WeatherSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.biome.Biome;

/** Off-duty shelter preference; never replaces work, emergencies or sleep. */
public final class VillagerSnowShelter {
    private BlockPos destination;
    private long nextSearch;

    public static boolean snowing(ServerLevel level, Villager body) {
        var pos = body.blockPosition();
        return level.isRaining() && WeatherSystem.precipitation(level, level.getBiome(pos).value(), pos)
            == Biome.Precipitation.SNOW;
    }

    private static boolean covered(ServerLevel level, BlockPos pos) {
        return LoadedChunks.has(level, pos) && !level.canSeeSky(pos)
            && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
            && level.getFluidState(pos).isEmpty()
            && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    public void tick(ServerLevel level, Villager body, long time) {
        var brain = body.getBrain();
        boolean free = brain.isActive(Activity.IDLE) || brain.isActive(Activity.MEET) || brain.isActive(Activity.PLAY);
        if (!free || !body.isAlive() || body.isSleeping() || body.isTrading() || !snowing(level, body)) {
            if (destination != null) {
                brain.getMemory(MemoryModuleType.WALK_TARGET).filter(target ->
                    target.getTarget().currentBlockPosition().equals(destination)).ifPresent(target -> {
                        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                        body.getNavigation().stop();
                    });
                destination = null;
            }
            return;
        }
        if (covered(level, body.blockPosition())) {
            // Indoor social movement is fine; reject a target which leads back into the snow.
            brain.getMemory(MemoryModuleType.WALK_TARGET).filter(target ->
                !covered(level, target.getTarget().currentBlockPosition())).ifPresent(target -> {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    brain.eraseMemory(MemoryModuleType.PATH);
                    body.getNavigation().stop();
                });
            destination = null;
            return;
        }
        if (destination != null && (!covered(level, destination) || time >= nextSearch)) destination = null;
        if (destination == null && time >= nextSearch) {
            nextSearch = time + 100;
            var home = brain.getMemory(MemoryModuleType.HOME)
                .filter(site -> site.dimension().equals(level.dimension()))
                .map(site -> site.pos()).orElse(null);
            if (home != null && home.distSqr(body.blockPosition()) <= 48 * 48) {
                for (var pos : BlockPos.betweenClosed(home.offset(-2, -1, -2), home.offset(2, 1, 2))) {
                    if (reachable(level, body, pos)) { destination = pos.immutable(); break; }
                }
            }
            if (destination == null) {
                var candidates = new ArrayList<BlockPos>();
                var origin = body.blockPosition();
                for (var pos : BlockPos.betweenClosed(origin.offset(-16, -4, -16), origin.offset(16, 4, 16)))
                    if (covered(level, pos)) candidates.add(pos.immutable());
                candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
                for (var pos : candidates.stream().limit(32).toList()) {
                    if (reachable(level, body, pos)) { destination = pos; break; }
                }
            }
        }
        if (destination != null) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, .5F, 0));
    }

    private static boolean reachable(ServerLevel level, Villager body, BlockPos pos) {
        if (!covered(level, pos)) return false;
        var path = body.getNavigation().createPath(pos, 0);
        return path != null && path.canReach();
    }
}
