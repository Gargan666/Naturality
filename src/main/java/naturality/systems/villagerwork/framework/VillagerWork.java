package naturality.systems.villagerwork.framework;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import naturality.systems.villagerwork.professions.ProfessionWork;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Registers villager work items and shared coordination with vanilla goal lists. */
public final class VillagerWork {
    private VillagerWork() { }
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> core(ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> original) {
        if (!naturality.config.startup.FeatureConfig.Feature.VILLAGER_GATES.enabled()) return original;
        var builder = ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder();
        builder.add(Pair.of(0, naturality.systems.villagerwork.gates.VillagerGates.create()));
        builder.addAll(original);
        return builder.build();
    }
    public static @org.jspecify.annotations.Nullable ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(Holder<VillagerProfession> profession) {
        if (!naturality.config.startup.FeatureConfig.Feature.VILLAGER_WORK.enabled()) return null;
        boolean farmer = profession.is(VillagerProfession.FARMER);
        if (profession.is(VillagerProfession.SHEPHERD) || profession.is(VillagerProfession.TOOLSMITH)
                || profession.is(VillagerProfession.WEAPONSMITH) || profession.is(VillagerProfession.ARMORER)) {
            if (!naturality.config.startup.FeatureConfig.Feature.ANIMAL_CARE.enabled()) return null;
            return ImmutableList.of(Pair.of(5, new naturality.systems.villagerwork.professions.AnimalCareWork(profession.is(VillagerProfession.SHEPHERD))),
                Pair.of(99, UpdateActivityFromSchedule.create()));
        }
        if (profession.is(VillagerProfession.BUTCHER)) {
            if (!naturality.config.startup.FeatureConfig.Feature.BUTCHER_WORK.enabled()) return null;
            return ImmutableList.of(Pair.of(5, new naturality.systems.villagerwork.professions.ButcherWork()),
                Pair.of(99, UpdateActivityFromSchedule.create()));
        }
        if (farmer || profession.is(VillagerProfession.FISHERMAN)) {
            if (!(farmer ? naturality.config.startup.FeatureConfig.Feature.FARMER_WORK.enabled() : naturality.config.startup.FeatureConfig.Feature.FISHERMAN_WORK.enabled())) return null;
            return ImmutableList.of(Pair.of(5, new ProfessionWork(farmer)),
                Pair.of(99, UpdateActivityFromSchedule.create()));
        }
        return null;
    }

    public interface VillagerWorkVisuals {
        @Nullable BlockPos naturality$castTarget();
        @Nullable Vec3 naturality$renderedRodTip();
        void naturality$setRenderedRodTip(@Nullable Vec3 tip);
        void naturality$setCastTarget(@Nullable BlockPos pos);
        boolean naturality$isDisplayingTrade();
        void naturality$setDisplayingTrade(boolean value);
    }

    /** One villager owns a narrow door until it clears the threshold. */
    public static final class DoorwayTraffic {
        private record Claim(UUID owner, int side, long until) {}
        private static final Map<ServerLevel, Map<BlockPos, Claim>> CLAIMS = new WeakHashMap<>();
        private static final Map<Villager, Boolean> WAITING = new WeakHashMap<>();
        public static boolean waiting(Villager body) { return WAITING.getOrDefault(body, false); }
        public static boolean yield(ServerLevel level, Villager body) {
            WAITING.remove(body);
            if (!naturality.config.startup.FeatureConfig.Feature.VILLAGER_GATES.enabled() || body.isSleeping() || body.isPassenger()) return false;
            var path = body.getNavigation().getPath();
            if (path == null || path.isDone()) return false;
            BlockPos door = null;
            for (int i = Math.max(0, path.getNextNodeIndex() - 1); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 5); i++) {
                var pos = path.getNode(i).asBlockPos();
                var state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof DoorBlock)) continue;
                if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) pos = pos.below();
                if (Vec3.atBottomCenterOf(pos).distanceToSqr(body.position()) <= 9) { door = pos; break; }
            }
            if (door == null || !headsThrough(level, body, door)) return false;
            var facing = level.getBlockState(door).getValue(DoorBlock.FACING);
            boolean xAxis = facing.getAxis() == net.minecraft.core.Direction.Axis.X;
            double center = xAxis ? door.getX() + .5 : door.getZ() + .5;
            var claims = CLAIMS.computeIfAbsent(level, _ -> new HashMap<>());
            long now = level.getGameTime();
            claims.entrySet().removeIf(entry -> entry.getValue().until < now);
            var claim = claims.get(door);
            var holder = claim == null ? null : level.getEntity(claim.owner);
            if (claim != null && (holder == null || !holder.isAlive() || holder.distanceToSqr(Vec3.atBottomCenterOf(door)) > 16
                    || ((xAxis ? holder.getX() : holder.getZ()) - center) * claim.side < -1.1)) {
                claims.remove(door); claim = null;
            }
            if (claim == null) {
                final BlockPos opening = door;
                var contenders = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(door).inflate(3),
                    other -> !other.isSleeping() && headsThrough(level, other, opening));
                var winner = contenders.stream().min(Comparator.<Villager>comparingInt(other -> other.getBrain().isActive(Activity.PANIC) ? 0 : 1)
                    .thenComparingDouble(other -> other.distanceToSqr(Vec3.atBottomCenterOf(opening)))
                    .thenComparing(Villager::getUUID)).orElse(body);
                double entry = (xAxis ? winner.getX() : winner.getZ()) - center;
                if (Math.abs(entry) < .1) {
                    var winnerPath = winner.getNavigation().getPath();
                    if (winnerPath != null && winnerPath.getNodeCount() > 0) {
                        var first = winnerPath.getNode(0);
                        entry = (xAxis ? first.x + .5 : first.z + .5) - center;
                    }
                }
                claim = new Claim(winner.getUUID(), entry < 0 ? -1 : 1, now + 200);
                claims.put(door, claim);
            }
            if (claim.owner.equals(body.getUUID())) return false;
            WAITING.put(body, true);
            body.setSpeed(0); body.setZza(0); body.setXxa(0);
            var velocity = body.getDeltaMovement();
            body.setDeltaMovement(0, velocity.y, 0);
            int side = (xAxis ? body.getX() : body.getZ()) < center ? -1 : 1;
            // Back away from the opening, freeing space for the owner to pass.
            if (Math.abs((xAxis ? body.getX() : body.getZ()) - center) < 1.8) {
                Vec3 away = xAxis ? new Vec3(side * .10, 0, 0) : new Vec3(0, 0, side * .10);
                if (level.noCollision(body, body.getBoundingBox().move(away))) body.setDeltaMovement(away.x, velocity.y, away.z);
            }
            return true;
        }
        private static boolean headsThrough(ServerLevel level, Villager body, BlockPos door) {
            var path = body.getNavigation().getPath();
            if (path == null || path.isDone()) return false;
            for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 5); i++) {
                var pos = path.getNode(i).asBlockPos();
                if (pos.equals(door) || pos.equals(door.above())) return true;
            }
            return false;
        }
        private DoorwayTraffic() {}
    }
}
