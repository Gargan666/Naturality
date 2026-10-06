package naturality.test;

import naturality.villager.ProfessionWork;
import naturality.villager.VillagerWorkVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Regression fixtures for reachable banks that cannot actually start a cast. */
final class FishermanCastingChecks {
    static void run(ServerLevel level, BlockPos job) {
        var body = new Villager(EntityTypes.VILLAGER, level);
        body.setVillagerData(body.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FISHERMAN));
        body.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), job));
        body.getBrain().setActiveActivityIfPossible(Activity.WORK);
        body.setNoAi(true);
        body.setOnGround(true);
        body.setPos(14.5, 101, .5);
        level.addFreshEntity(body);
        var water = new BlockPos(18, 100, 0);
        var stand = new BlockPos(14, 101, 0);
        var wall = new BlockPos(16, 101, 0);
        var old = level.getBlockState(wall);
        var oldAbove = level.getBlockState(wall.above());
        ProfessionWork work = new ProfessionWork(false);
        long now = level.getGameTime();
        try {
            var targetType = Class.forName("naturality.villager.ProfessionWork$Target");
            var taskType = Class.forName("naturality.villager.ProfessionWork$Task");
            Object fish = java.util.Arrays.stream(taskType.getEnumConstants()).filter(t -> t.toString().equals("FISH")).findFirst().orElseThrow();
            var constructor = targetType.getDeclaredConstructor(BlockPos.class, BlockPos.class, taskType);
            constructor.setAccessible(true);
            Object target = constructor.newInstance(water, stand, fish);
            check(work.tryStart(level, body, now), "Fisherman starts fixture work");
            set(work, "target", target); set(work, "fishingSpot", target);
            set(work, "fishingJob", job); set(work, "fishingLevel", level);
            set(work, "targetSince", now); set(work, "nextStation", now + 600);
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
            for (int i = 0; i < 60; i++) work.tickOrStop(level, body, now + i);
            check(get(work, "fishingSpot") != target, "Blocked bank must be forgotten instead of cached forever");
            check(get(work, "target") != target, "Fisherman must leave a bank where casting is blocked");
            work.doStop(level, body, now + 60);
            level.setBlockAndUpdate(wall, old); level.setBlockAndUpdate(wall.above(), oldAbove);

            // All barrel approaches are blocked, but fishing itself is still possible.
            var blocked = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos p = job.relative(dir); blocked.put(p, level.getBlockState(p));
                level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
            }
            try {
                work = new ProfessionWork(false);
                check(work.tryStart(level, body, now), "Fisherman restarts with an inaccessible barrel");
                set(work, "fishingSpot", target); set(work, "fishingJob", job); set(work, "fishingLevel", level);
                work.tickOrStop(level, body, now);
                check(body.getMainHandItem().is(Items.FISHING_ROD), "Unavailable barrel must not prevent equipping a rod");
                check(((VillagerWorkVisuals)body).naturality$castTarget() != null,
                    "Unavailable barrel must not cancel each cast before it starts");
                work.doStop(level, body, now + 1);
            } finally { blocked.forEach((p, state) -> level.setBlockAndUpdate(p, state)); }
            System.out.println("Fisherman recovery: blocked casting spot rejected; inaccessible barrel still permits rod and cast");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        finally {
            work.doStop(level, body, now + 100);
            body.discard();
            level.setBlockAndUpdate(wall, old); level.setBlockAndUpdate(wall.above(), oldAbove);
        }
    }
    private static void set(Object work, String name, Object value) throws ReflectiveOperationException {
        var field = ProfessionWork.class.getDeclaredField(name); field.setAccessible(true); field.set(work, value);
    }
    private static Object get(Object work, String name) throws ReflectiveOperationException {
        var field = ProfessionWork.class.getDeclaredField(name); field.setAccessible(true); return field.get(work);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
