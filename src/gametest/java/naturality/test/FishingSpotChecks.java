package naturality.test;

import naturality.villager.FishingSpots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

final class FishingSpotChecks {
    static void run(ServerLevel level, BlockPos origin) {
        try {
            var tick = FishingSpots.class.getDeclaredMethod("tick", ServerLevel.class);
            tick.setAccessible(true);
            check(FishingSpots.nearby(level, origin).isEmpty(), "First request only queues bounded survey work");
            for (int i = 0; i < 17; i++) {
                for (int villager = 0; villager < 40; villager++)
                    FishingSpots.nearby(level, origin.offset(villager % 4, 0, villager % 3));
                tick.invoke(null, level);
            }
            check(FishingSpots.nearby(level, origin).isEmpty(), "8712 cells cannot exceed the shared 512-cell tick budget");
            tick.invoke(null, level);
            var spots = FishingSpots.nearby(level, origin);
            check(!spots.isEmpty(), "Local pond yields fishing spots after 18 budgeted ticks");
            for (int i = 0; i < 40; i++)
                check(FishingSpots.nearby(level, origin.offset(i % 4, 0, i % 3)) == spots,
                    "Nearby fishermen reuse the identical completed survey");
            tick.invoke(null, level);
            check(FishingSpots.nearby(level, origin) == spots, "Completed surveys do not repeat on ticks or visits");
            for (var spot : spots) {
                check(FishingSpots.usable(level, spot), "Published shore/water pair is usable");
                check(Math.abs(spot.water().getX() - origin.getX()) <= 16
                    && Math.abs(spot.water().getZ() - origin.getZ()) <= 16, "Search stays local");
            }
            BlockPos unrelated = origin.offset(-8, 0, 4);
            var unrelatedState = level.getBlockState(unrelated);
            level.setBlockAndUpdate(unrelated, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(unrelated, unrelatedState);
            check(FishingSpots.nearby(level, origin) == spots,
                "Unrelated farming/building changes must not restart shoreline surveys");
            BlockPos removed = spots.getFirst().water();
            var before = level.getBlockState(removed);
            level.setBlockAndUpdate(removed, Blocks.STONE.defaultBlockState());
            check(FishingSpots.nearby(level, origin).isEmpty(), "Real block updates invalidate shared results");
            level.setBlockAndUpdate(removed, before);
            FishingSpots.nearby(level, origin);
            for (int i = 0; i < 18; i++) tick.invoke(null, level);
            var refreshed = FishingSpots.nearby(level, origin);
            check(!refreshed.isEmpty() && refreshed != spots, "Edited shore is surveyed again once");
            BlockPos cover = removed.above();
            var coverState = level.getBlockState(cover);
            level.setBlockAndUpdate(cover, Blocks.STONE.defaultBlockState());
            FishingSpots.nearby(level, origin);
            for (int i = 0; i < 18; i++) tick.invoke(null, level);
            check(FishingSpots.nearby(level, origin).stream().noneMatch(spot -> spot.water().equals(removed)),
                "Covered water cannot be selected");
            level.setBlockAndUpdate(cover, coverState);
            check(FishingSpots.nearby(level, origin).isEmpty(),
                "Uncovering previously unusable water must invalidate the survey too");
            for (int i = 0; i < 18; i++) tick.invoke(null, level);
            BlockPos far = new BlockPos(1000000, origin.getY(), 1000000);
            FishingSpots.nearby(level, far);
            for (int i = 0; i < 18; i++) tick.invoke(null, level);
            check(FishingSpots.nearby(level, far).isEmpty(), "Unloaded area remains empty");
            check(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "Shared surveys never load distant chunks");
            System.out.println("Fishing survey: 40 nearby requests reused one bounded scan; edit invalidation passed");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
