package naturality.test;

import naturality.chorus.ChorusFlowers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ChorusFlowersGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void tick(ServerLevel level, BlockPos pos, long seed) {
        try {
            var method = ChorusFlowerBlock.class.getDeclaredMethod("randomTick",
                net.minecraft.world.level.block.state.BlockState.class, ServerLevel.class,
                BlockPos.class, RandomSource.class);
            method.setAccessible(true);
            method.invoke(Blocks.CHORUS_FLOWER, level.getBlockState(pos), level, pos, RandomSource.create(seed));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 103 -8 0 15");
            server.runOnServer(s -> {
                var level = s.getLevel(Level.OVERWORLD);
                var player = level.players().getFirst();
                int i = 0;
                for (Direction facing : Direction.values()) {
                    BlockPos pos = new BlockPos(i++ * 5, 100, 0);
                    BlockPos support = pos.relative(facing.getOpposite());
                    level.setBlock(support, Blocks.END_STONE.defaultBlockState(), 3);
                    var stack = new ItemStack(Items.CHORUS_FLOWER);
                    var hit = new BlockHitResult(Vec3.atCenterOf(support), facing, support, false);
                    var placement = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
                    ((BlockItem) Items.CHORUS_FLOWER).place(placement);
                    var state = level.getBlockState(pos);
                    check(state.is(Blocks.CHORUS_FLOWER), "Real item placement failed: " + facing);
                    check(state.getValue(ChorusFlowers.FACING) == facing, "Clicked face was not retained");
                    check(state.canSurvive(level, pos), "Directional attachment cannot survive");
                    state = state.setValue(ChorusFlowers.BLOOM, false);
                    level.setBlock(pos, state, 3);
                    var bounds = state.getCollisionShape(level, pos).bounds();
                    double size = switch (facing.getAxis()) {
                        case X -> bounds.maxX - bounds.minX;
                        case Y -> bounds.maxY - bounds.minY;
                        case Z -> bounds.maxZ - bounds.minZ;
                    };
                    check(Math.abs(size - 7.0 / 16) < 1e-8, "Normal hitbox is not 7 pixels along facing");
                    check(state.getShape(level, pos).bounds().equals(bounds), "Outline and collision disagree");
                    var bloom = state.setValue(ChorusFlowers.BLOOM, true);
                    check(bloom.getCollisionShape(level, pos).bounds().getYsize() == 1,
                        "Bloom hitbox should retain full height");
                    tick(level, pos, 7);
                    BlockPos next = pos.relative(facing);
                    check(level.getBlockState(pos).is(Blocks.CHORUS_PLANT), "Growth did not leave a stem: " + facing);
                    check(level.getBlockState(pos).canSurvive(level, pos), "Directional stem loses its root");
                    check(level.getBlockState(next).is(Blocks.CHORUS_FLOWER)
                        && level.getBlockState(next).getValue(ChorusFlowers.FACING) == facing,
                        "Natural growth did not continue in its facing direction: " + facing);
                    level.setBlock(support, Blocks.AIR.defaultBlockState(), 3);
                    check(!level.getBlockState(pos).canSurvive(level, pos), "Detached stem must lose support");
                }

                // Force branching on long stems in every local frame. This reaches hanging buds too.
                var directions = new java.util.HashSet<Direction>();
                for (int seed = 0; seed < 160; seed++) {
                    Direction facing = Direction.values()[seed % 6];
                    BlockPos pos = new BlockPos(40 + (seed % 16) * 10, 110, 30 + (seed / 16) * 10);
                    Direction back = facing.getOpposite();
                    level.setBlock(pos.relative(back, 6), Blocks.END_STONE.defaultBlockState(), 2);
                    for (int length = 1; length <= 5; length++)
                        level.setBlock(pos.relative(back, length), Blocks.CHORUS_PLANT.defaultBlockState(), 2);
                    boolean parentBloom = seed % 2 == 0;
                    level.setBlock(pos, ChorusFlowers.flower(facing, 1, RandomSource.create(seed))
                        .setValue(ChorusFlowers.BLOOM, parentBloom), 2);
                    tick(level, pos, seed);
                    for (Direction direction : Direction.values()) {
                        var branch = level.getBlockState(pos.relative(direction));
                        if (direction.getAxis() != facing.getAxis() && branch.is(Blocks.CHORUS_FLOWER)) {
                            check(branch.getValue(ChorusFlowers.FACING) == direction, "Branch must face away from parent");
                            check(branch.getValue(ChorusFlowers.BLOOM) == parentBloom, "Branch rerolled its parent variant");
                            check(branch.canSurvive(level, pos.relative(direction)), "Branch detached from stem");
                            directions.add(direction);
                        }
                    }
                }
                check(directions.size() == 6, "Natural branches did not exercise all six directions: " + directions);

                for (boolean parentBloom : new boolean[]{false, true}) {
                    int index = 0;
                    for (Direction facing : Direction.values()) {
                        BlockPos pos = new BlockPos(-40 - index++ * 8, parentBloom ? 120 : 100, 20);
                        level.setBlock(pos.relative(facing.getOpposite()), Blocks.END_STONE.defaultBlockState(), 2);
                        level.setBlock(pos, ChorusFlowers.flower(facing, 0, RandomSource.create(1))
                            .setValue(ChorusFlowers.BLOOM, parentBloom), 2);
                        for (int generation = 0; generation < 2; generation++) {
                            tick(level, pos, 10 + generation);
                            pos = pos.relative(facing);
                            var grown = level.getBlockState(pos);
                            check(grown.is(Blocks.CHORUS_FLOWER) && grown.getValue(ChorusFlowers.BLOOM) == parentBloom,
                                "Forward growth changed variant: " + facing + ", bloom=" + parentBloom);
                        }
                    }
                }

                // A pre-orientation lateral flower must retain its variant when migrated.
                BlockPos legacy = new BlockPos(-20, 100, 0);
                level.setBlock(legacy.west().below(), Blocks.END_STONE.defaultBlockState(), 2);
                level.setBlock(legacy.west(), Blocks.CHORUS_PLANT.defaultBlockState(), 2);
                level.setBlock(legacy, ChorusFlowers.flower(Direction.UP, 0, RandomSource.create(1))
                    .setValue(ChorusFlowers.BLOOM, true), 2);
                tick(level, legacy, 2);
                check(level.getBlockState(legacy.east()).is(Blocks.CHORUS_FLOWER)
                    && level.getBlockState(legacy.east()).getValue(ChorusFlowers.BLOOM),
                    "Legacy lateral growth lost its bloom variant");

                BlockPos dead = new BlockPos(0, 100, 20);
                var dying = ChorusFlowers.flower(Direction.EAST, 4, RandomSource.create(0)).setValue(ChorusFlowers.BLOOM, true);
                level.setBlock(dead.west(), Blocks.END_STONE.defaultBlockState(), 2);
                level.setBlock(dead.east().above(), Blocks.END_STONE.defaultBlockState(), 2);
                level.setBlock(dead, dying, 2);
                tick(level, dead, 5);
                var finished = level.getBlockState(dead);
                check(finished.getValue(ChorusFlowerBlock.AGE) == 5 && finished.getValue(ChorusFlowers.BLOOM)
                    && finished.getValue(ChorusFlowers.FACING) == Direction.EAST, "Death rerolled the variant or orientation");

                int blooms = 0;
                var random = RandomSource.create(42);
                for (int sample = 0; sample < 10000; sample++)
                    if (ChorusFlowers.flower(Direction.UP, 0, random).getValue(ChorusFlowers.BLOOM)) blooms++;
                check(blooms > 850 && blooms < 1150, "Bloom probability drifted from 10%: " + blooms);

                BlockPos crownStem = new BlockPos(-12, 110, -25);
                level.setBlock(crownStem.below(4), Blocks.END_STONE.defaultBlockState(), 2);
                for (int length = 0; length < 4; length++)
                    level.setBlock(crownStem.below(length), Blocks.CHORUS_PLANT.defaultBlockState(), 2);
                var crownFlower = ChorusFlowers.flower(Direction.UP, 5, RandomSource.create(1))
                    .setValue(ChorusFlowers.BLOOM, true);
                int crowns = 0;
                var crownRandom = RandomSource.create(1849);
                for (int sample = 0; sample < 1000; sample++) {
                    for (Direction direction : Direction.values()) {
                        if (direction != Direction.DOWN)
                            level.setBlock(crownStem.relative(direction), Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(crownStem.above(), crownFlower, 2);
                    if (!ChorusFlowers.crown(level, crownStem, crownFlower, crownRandom)) continue;
                    crowns++;
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos target = crownStem.relative(direction);
                        var extra = level.getBlockState(target);
                        check(extra.is(Blocks.CHORUS_FLOWER) && extra.getValue(ChorusFlowerBlock.AGE) == 5
                            && extra.getValue(ChorusFlowers.FACING) == direction && extra.getValue(ChorusFlowers.BLOOM),
                            "Mature crown missing an outward dead flower with inherited variant");
                        check(extra.canSurvive(level, target), "Crown flower has no support");
                        tick(level, target, sample);
                        check(level.getBlockState(target).equals(extra), "Dead crown flower continued growing");
                    }
                }
                check(crowns > 150 && crowns < 250, "Crown probability differs from 20%: " + crowns);
                for (Direction direction : Direction.Plane.HORIZONTAL)
                    level.setBlock(crownStem.relative(direction), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(crownStem.north(), Blocks.END_STONE.defaultBlockState(), 2);
                boolean filled = false;
                for (int seed = 0; seed < 50 && !filled; seed++)
                    filled = ChorusFlowers.crown(level, crownStem, crownFlower, RandomSource.create(seed));
                check(filled && level.getBlockState(crownStem.north()).is(Blocks.END_STONE),
                    "Crown replaced an occupied surface");
                BlockPos smallStem = crownStem.offset(-8, 0, 0);
                level.setBlock(smallStem.below(2), Blocks.END_STONE.defaultBlockState(), 2);
                level.setBlock(smallStem.below(), Blocks.CHORUS_PLANT.defaultBlockState(), 2);
                level.setBlock(smallStem, Blocks.CHORUS_PLANT.defaultBlockState(), 2);
                for (int seed = 0; seed < 50; seed++)
                    check(!ChorusFlowers.crown(level, smallStem, crownFlower, RandomSource.create(seed)),
                        "A young shoot received a mature crown");

                // Exercise the actual transition to maturity, rather than only the crown helper.
                level.setBlock(crownStem.north(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(crownStem.above(2).north(), Blocks.END_STONE.defaultBlockState(), 2);
                boolean maturedCrown = false;
                for (int seed = 0; seed < 50 && !maturedCrown; seed++) {
                    for (Direction direction : Direction.Plane.HORIZONTAL)
                        level.setBlock(crownStem.relative(direction), Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(crownStem.above(), crownFlower.setValue(ChorusFlowerBlock.AGE, 4), 2);
                    tick(level, crownStem.above(), seed);
                    maturedCrown = level.getBlockState(crownStem.east()).is(Blocks.CHORUS_FLOWER);
                }
                check(maturedCrown, "Natural maturity never produced a flower crown");

                int generated = 0;
                BlockPos connectionStem = new BlockPos(-12, 100, -10);
                level.setBlock(connectionStem.below(2), Blocks.END_STONE.defaultBlockState(), 2);
                level.setBlock(connectionStem.below(), Blocks.CHORUS_PLANT.defaultBlockState(), 2);
                level.setBlock(connectionStem, net.minecraft.world.level.block.ChorusPlantBlock.getStateWithConnections(level, connectionStem,
                    Blocks.CHORUS_PLANT.defaultBlockState()), 2);
                for (Direction direction : Direction.values()) {
                    BlockPos adjacent = connectionStem.relative(direction);
                    var property = net.minecraft.world.level.block.ChorusPlantBlock.PROPERTY_BY_DIRECTION.get(direction);
                    var side = ChorusFlowers.flower(direction.getOpposite(), 5, RandomSource.create(1));
                    level.setBlock(adjacent, side, 3);
                    check(!level.getBlockState(connectionStem).getValue(property), "Stem connected to a flower's side/tip: " + direction);
                    var computed = net.minecraft.world.level.block.ChorusPlantBlock.getStateWithConnections(level,
                        connectionStem, Blocks.CHORUS_PLANT.defaultBlockState());
                    check(!computed.getValue(property), "Computed connection ignored flower facing");
                    level.setBlock(adjacent, side.setValue(ChorusFlowers.FACING, direction), 3);
                    check(level.getBlockState(connectionStem).getValue(property), "Stem failed to connect to flower base: " + direction);
                    level.setBlock(adjacent, Blocks.AIR.defaultBlockState(), 3);
                    check(!level.getBlockState(connectionStem).getValue(property), "Removed flower left a stale stem arm");
                    if (direction == Direction.DOWN)
                        level.setBlock(adjacent, Blocks.CHORUS_PLANT.defaultBlockState(), 3);
                }
                int generatedCrowns = 0;
                boolean generatedSideways = false;
                for (int seed = 0; seed < 16; seed++) {
                    BlockPos base = new BlockPos(40 + (seed % 4) * 24, 100, -40 - (seed / 4) * 24);
                    level.setBlock(base.below(), Blocks.END_STONE.defaultBlockState(), 2);
                    ChorusFlowerBlock.generatePlant(level, base, RandomSource.create(seed), 6);
                    for (BlockPos flowerPos : BlockPos.betweenClosed(base.offset(-7, 0, -7), base.offset(7, 30, 7))) {
                        var flower = level.getBlockState(flowerPos);
                        if (flower.is(Blocks.CHORUS_PLANT)) {
                            int attached = 0;
                            for (Direction direction : Direction.values()) {
                                var neighbor = level.getBlockState(flowerPos.relative(direction));
                                boolean connection = neighbor.is(Blocks.CHORUS_PLANT)
                                    || (neighbor.is(Blocks.CHORUS_FLOWER) && neighbor.getValue(ChorusFlowers.FACING) == direction)
                                    || (direction == Direction.DOWN && neighbor.is(net.minecraft.tags.BlockTags.SUPPORTS_CHORUS_PLANT));
                                check(flower.getValue(net.minecraft.world.level.block.ChorusPlantBlock.PROPERTY_BY_DIRECTION.get(direction))
                                    == connection, "Generated stem has a missing or invalid arm at " + flowerPos + " toward " + direction);
                                if (neighbor.is(Blocks.CHORUS_FLOWER)
                                        && neighbor.getValue(ChorusFlowers.FACING) == direction) {
                                    check(neighbor.getValue(ChorusFlowerBlock.AGE) == 5,
                                        "Worldgen crown flowers must already be dead");
                                    attached++;
                                }
                            }
                            if (attached >= 3) {
                                generatedCrowns++;
                                for (Direction direction : Direction.values())
                                    check(!level.isEmptyBlock(flowerPos.relative(direction)),
                                        "Initial worldgen crown left an exposed face empty");
                            }
                        }
                        if (!flower.is(Blocks.CHORUS_FLOWER)) continue;
                        generated++;
                        check(flower.canSurvive(level, flowerPos), "Generated flower has no directional support");
                        if (flower.getValue(ChorusFlowers.FACING) != Direction.UP) generatedSideways = true;
                    }
                }
                check(generated > 16 && generatedSideways, "Generated End trees lack oriented flowers");
                check(generatedCrowns > 0, "Initial worldgen trees did not receive mature flower crowns");

                // A compact display for model rotation and synced state verification.
                i = 0;
                for (Direction facing : Direction.values()) {
                    BlockPos pos = new BlockPos(-8 + i++ * 3, 101, -5);
                    level.setBlock(pos.relative(facing.getOpposite()), Blocks.END_STONE.defaultBlockState(), 3);
                    level.setBlock(pos, ChorusFlowers.flower(facing, 5, RandomSource.create(1))
                        .setValue(ChorusFlowers.BLOOM, false), 3);
                    BlockPos bloomPos = pos.offset(0, 4, 0);
                    level.setBlock(bloomPos.relative(facing.getOpposite()), Blocks.END_STONE.defaultBlockState(), 3);
                    level.setBlock(bloomPos, ChorusFlowers.flower(facing, 5, RandomSource.create(1))
                        .setValue(ChorusFlowers.BLOOM, true), 3);
                }
            });
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 104 -17 0 0");
            context.waitTicks(8);
            server.runOnServer(s -> {
                var level = s.getLevel(Level.OVERWORLD);
                int i = 0;
                for (Direction facing : Direction.values()) {
                    BlockPos pos = new BlockPos(i++ * 5, 100, 0);
                    check(level.getBlockState(pos).isAir() && level.getBlockState(pos.relative(facing)).isAir(),
                        "Support removal did not break the stem and flower: " + facing);
                }
            });
            context.runOnClient(client -> {
                int i = 0;
                for (Direction facing : Direction.values()) {
                    BlockPos pos = new BlockPos(-8 + i++ * 3, 101, -5);
                    var state = client.level.getBlockState(pos);
                    check(state.getValue(ChorusFlowers.FACING) == facing && !state.getValue(ChorusFlowers.BLOOM),
                        "Client did not receive orientation/normal variant");
                    check(client.level.getBlockState(pos.above(4)).getValue(ChorusFlowers.BLOOM),
                        "Client did not receive bloom variant");
                }
            });
            context.takeScreenshot("chorus-six-orientations-normal-and-bloom");
        }
    }
}
