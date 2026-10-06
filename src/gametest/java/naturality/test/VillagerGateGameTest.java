package naturality.test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import naturality.villager.VillagerGates;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

public final class VillagerGateGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Villager villager(ServerLevel level, double z) {
        var body = new Villager(EntityTypes.VILLAGER, level);
        body.setPos(.5, 101, z); body.setOnGround(true); body.setVillagerXp(1);
        body.refreshBrain(level);
        body.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.IDLE);
        level.addFreshEntity(body);
        return body;
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("time set 1000");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 2 104 -4 facing 0 101 0");
            var gate = new BlockPos(0, 101, 0);
            var destination = new BlockPos(0, 101, 5);
            server.runOnServer(s -> {
                var level = s.overworld();
                for (var pos : BlockPos.betweenClosed(-3, 100, -9, 3, 104, 9)) {
                    boolean wall = pos.getY() > 100 && (pos.getX() == -1 || pos.getX() == 1 || Math.abs(pos.getZ()) == 9);
                    level.setBlockAndUpdate(pos, pos.getY() == 100 || wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            });
            for (var block : List.of(Blocks.OAK_FENCE_GATE, Blocks.CRIMSON_FENCE_GATE, Blocks.CHERRY_FENCE_GATE, Blocks.OAK_DOOR)) {
                Villager[] runner = new Villager[1];
                server.runOnServer(s -> {
                    var level = s.overworld();
                    level.setBlockAndUpdate(gate.above(), Blocks.AIR.defaultBlockState());
                    var state = block.defaultBlockState();
                    if (block instanceof FenceGateBlock) state = state.setValue(FenceGateBlock.FACING, Direction.NORTH);
                    else state = state.setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
                    level.setBlockAndUpdate(gate, state);
                    if (block instanceof DoorBlock) level.setBlockAndUpdate(gate.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    runner[0] = villager(level, -4.5);
                    var path = runner[0].getNavigation().createPath(destination, 0);
                    check(path != null && path.canReach(), "Villager can plan through closed " + block);
                    boolean through = false;
                    for (int i = 0; i < path.getNodeCount(); i++) through |= path.getNode(i).asBlockPos().equals(gate);
                    check(through, "Path passes through the opening rather than over it");
                    if (block instanceof FenceGateBlock) {
                        var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
                        cow.setPos(.5, 101, -4.5); cow.setOnGround(true);
                        var cowPath = cow.getNavigation().createPath(destination, 0);
                        check(cowPath == null || !cowPath.canReach(), "Livestock cannot plan through closed gates");
                    }
                    runner[0].getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, .5F, 0));
                });
                boolean[] opened = {false}, crossed = {false}, closed = {false};
                for (int i = 0; i < 240 && !closed[0]; i += 2) {
                    context.waitTicks(2);
                    server.runOnServer(s -> {
                        var state = s.overworld().getBlockState(gate);
                        boolean open = state.getValue(FenceGateBlock.OPEN);
                        opened[0] |= open;
                        crossed[0] |= runner[0].getZ() > 2;
                        closed[0] = opened[0] && crossed[0] && (block instanceof DoorBlock || !open);
                        if (!runner[0].getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) && !crossed[0])
                            runner[0].getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, .5F, 0));
                    });
                }
                server.runOnServer(s -> {
                    check(opened[0] && crossed[0] && closed[0], "Villager opens and passes the barrier, and closes fence gates: " + block
                        + " opened=" + opened[0] + " crossed=" + crossed[0] + " closed=" + closed[0]
                        + " gate=" + s.overworld().getBlockState(gate) + " position=" + runner[0].position() + " path=" + runner[0].getNavigation().getPath()
                        + " brainPath=" + runner[0].getBrain().getMemory(MemoryModuleType.PATH)
                        + " memory=" + runner[0].getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                        + " id=" + runner[0].getId() + " noAI=" + runner[0].isNoAi() + " ticks=" + runner[0].tickCount
                        + " removed=" + runner[0].isRemoved() + " live=" + (s.overworld().getEntity(runner[0].getId()) == runner[0])
                        + " activities=" + runner[0].getBrain().getActiveActivities()
                        + " behavior=" + runner[0].getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList());
                    runner[0].discard();
                });
            }
            server.runOnServer(s -> {
                var level = s.overworld();
                level.setBlockAndUpdate(gate.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
                var leader = villager(level, 2.5);
                var follower = villager(level, -.5);
                var route = new Path(List.of(new Node(0, 101, -1), new Node(0, 101, 0), new Node(0, 101, 1)), destination, true);
                route.advance();
                follower.getBrain().setMemory(MemoryModuleType.PATH, route);
                var remembered = new HashSet<>(List.of(GlobalPos.of(level.dimension(), gate)));
                InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, leader, null, null, remembered, Optional.of(List.of(follower)));
                check(level.getBlockState(gate).getValue(FenceGateBlock.OPEN), "Gate stays open for a following villager on the same path");
                check(remembered.isEmpty(), "Following villager takes responsibility for closing, as with doors");
                follower.getBrain().eraseMemory(MemoryModuleType.PATH);
                remembered.add(GlobalPos.of(level.dimension(), gate));
                InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, leader, null, null, remembered, Optional.of(List.of(follower)));
                check(!level.getBlockState(gate).getValue(FenceGateBlock.OPEN), "Gate closes once nobody else needs it");
                level.setBlock(gate, level.getBlockState(gate).setValue(FenceGateBlock.OPEN, true).setValue(FenceGateBlock.POWERED, true), 2);
                remembered.add(GlobalPos.of(level.dimension(), gate));
                VillagerGates.close(level, leader, null, null, remembered, Optional.empty());
                check(level.getBlockState(gate).getValue(FenceGateBlock.OPEN), "Villagers leave redstone-powered gates open");
                leader.discard(); follower.discard();
            });
            context.takeScreenshot("villager-fence-gates");
        }
    }
}
