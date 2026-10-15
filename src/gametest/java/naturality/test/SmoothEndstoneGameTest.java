package naturality.test;

import naturality.NaturalityBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import naturality.chorus.ChorusFlowers;
import net.minecraft.util.RandomSource;

public final class SmoothEndstoneGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("setblock 0 100 0 naturality:smooth_endstone");
            server.runCommand("give @a naturality:smooth_endstone");
            server.runCommand("setblock 2 100 0 naturality:lirestone");
            server.runCommand("give @a naturality:lirestone");
            server.runOnServer(s -> { for (int cx = 8; cx <= 11; cx++) for (int cz = -2; cz <= 1; cz++) s.getLevel(Level.OVERWORLD).getChunk(cx, cz); });
            server.runCommand("fillbiome 144 92 -16 176 108 16 minecraft:forest");
            server.runOnServer(s -> {
                var level = s.getLevel(Level.OVERWORLD);
                BlockPos pos = new BlockPos(0, 100, 0);
                var state = level.getBlockState(pos);
                var vanilla = Blocks.END_STONE.defaultBlockState();
                check(state.is(NaturalityBlocks.SMOOTH_ENDSTONE), "Block command registration failed");
                check(state.getDestroySpeed(level, pos) == vanilla.getDestroySpeed(level, pos), "Hardness differs");
                check(state.getBlock().getExplosionResistance() == Blocks.END_STONE.getExplosionResistance(), "Blast resistance differs");
                check(state.getSoundType() == vanilla.getSoundType(), "Sound differs");
                check(state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.is(BlockTags.DRAGON_IMMUNE), "End Stone tags missing");
                check(NaturalityBlocks.SMOOTH_ENDSTONE.asItem() == NaturalityBlocks.SMOOTH_ENDSTONE_ITEM, "Block item mapping missing");
                var drops = Block.getDrops(state, level, pos, null, level.players().getFirst(), new ItemStack(Items.IRON_PICKAXE));
                check(drops.size() == 1 && drops.getFirst().is(Items.END_STONE), "Smooth End Stone must drop End Stone");
                var silkTool = new ItemStack(Items.IRON_PICKAXE);
                silkTool.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
                var silkDrops = Block.getDrops(state, level, pos, null, level.players().getFirst(), silkTool);
                check(silkDrops.size() == 1 && silkDrops.getFirst().is(NaturalityBlocks.SMOOTH_ENDSTONE_ITEM),
                    "Silk Touch pickaxe must drop Smooth End Stone");
                BlockPos furnacePos = pos.offset(0, 0, 3);
                level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
                var furnace = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)level.getBlockEntity(furnacePos);
                furnace.setItem(0, new ItemStack(Items.END_STONE));
                furnace.setItem(1, new ItemStack(Items.COAL));
                for (int tick = 0; tick < 205; tick++)
                    net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level, furnacePos,
                        level.getBlockState(furnacePos), furnace);
                check(furnace.getItem(2).is(NaturalityBlocks.SMOOTH_ENDSTONE_ITEM) && furnace.getItem(2).getCount() == 1,
                    "Furnace must smelt End Stone into Smooth End Stone");
                check(ChorusFlowers.flower(Direction.UP, 0, RandomSource.create(1)).canSurvive(level, pos.above()),
                    "Smooth End Stone must support chorus flowers");
                BlockPos lirestonePos = pos.offset(2, 0, 0);
                var lirestone = level.getBlockState(lirestonePos);
                check(lirestone.is(NaturalityBlocks.LIRESTONE), "Lirestone block registration failed");
                check(lirestone.getDestroySpeed(level, lirestonePos) == 2.5F
                    && lirestone.getBlock().getExplosionResistance() == Blocks.END_STONE.getExplosionResistance()
                    && lirestone.getSoundType() == naturality.NaturalitySounds.LIRESTONE, "Lirestone hardness/resistance/sound incorrect");
                check(lirestone.getSoundType().getBreakSound() == naturality.NaturalitySounds.LIRESTONE_BREAK
                    && lirestone.getSoundType().getPlaceSound() == naturality.NaturalitySounds.LIRESTONE_PLACE
                    && lirestone.getSoundType().getStepSound() == naturality.NaturalitySounds.LIRESTONE_STEP
                    && lirestone.getSoundType().getHitSound() == naturality.NaturalitySounds.LIRESTONE_BREAK
                    && lirestone.getSoundType().getFallSound() == naturality.NaturalitySounds.LIRESTONE_STEP,
                    "Lirestone custom sound bindings incorrect");
                check(lirestone.is(BlockTags.MINEABLE_WITH_PICKAXE) && lirestone.is(BlockTags.DRAGON_IMMUNE),
                    "Lirestone End Stone tags missing");
                check(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(lirestone), "Wooden pickaxe must harvest Lirestone");
                var lirestoneItem = new ItemStack(NaturalityBlocks.LIRESTONE_ITEM);
                check(lirestoneItem.is(net.minecraft.tags.ItemTags.STONE_CRAFTING_MATERIALS)
                    && lirestoneItem.is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS), "Lirestone stone material tags missing");
                check(NaturalityBlocks.LIRESTONE.asItem() == NaturalityBlocks.LIRESTONE_ITEM, "Lirestone block item mapping missing");
                var lirestoneDrops = Block.getDrops(lirestone, level, lirestonePos, null, level.players().getFirst(),
                    new ItemStack(Items.IRON_PICKAXE));
                check(lirestoneDrops.size() == 1 && lirestoneDrops.getFirst().is(NaturalityBlocks.LIRESTONE_ITEM),
                    "Lirestone should drop itself");

                BlockPos smoothLirePos = pos.offset(6, 0, 0);
                var smoothLire = NaturalityBlocks.SMOOTH_LIRESTONE.defaultBlockState();
                level.setBlockAndUpdate(smoothLirePos, smoothLire);
                check(smoothLire.getDestroySpeed(level, smoothLirePos) == 2.5F
                    && smoothLire.getBlock().getExplosionResistance() == lirestone.getBlock().getExplosionResistance()
                    && smoothLire.getSoundType() == lirestone.getSoundType(), "Smooth Lirestone properties must match Lirestone");
                check(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(smoothLire)
                    && smoothLire.is(BlockTags.DRAGON_IMMUNE), "Smooth Lirestone mining/dragon tags missing");
                var smoothLireItem = new ItemStack(NaturalityBlocks.SMOOTH_LIRESTONE_ITEM);
                check(smoothLireItem.is(net.minecraft.tags.ItemTags.STONE_CRAFTING_MATERIALS)
                    && smoothLireItem.is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS), "Smooth Lirestone stone material tags missing");
                var regularLireDrops = Block.getDrops(smoothLire, level, smoothLirePos, null, level.players().getFirst(), new ItemStack(Items.WOODEN_PICKAXE));
                check(regularLireDrops.size() == 1 && regularLireDrops.getFirst().is(NaturalityBlocks.LIRESTONE_ITEM), "Smooth Lirestone must drop Lirestone");
                var silkLireDrops = Block.getDrops(smoothLire, level, smoothLirePos, null, level.players().getFirst(), silkTool);
                check(silkLireDrops.size() == 1 && silkLireDrops.getFirst().is(NaturalityBlocks.SMOOTH_LIRESTONE_ITEM), "Silk Touch must drop Smooth Lirestone");
                furnace.setItem(0, new ItemStack(NaturalityBlocks.LIRESTONE_ITEM));
                furnace.setItem(2, ItemStack.EMPTY);
                for (int tick = 0; tick < 205; tick++)
                    net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level, furnacePos, level.getBlockState(furnacePos), furnace);
                check(furnace.getItem(2).is(NaturalityBlocks.SMOOTH_LIRESTONE_ITEM), "Furnace must smelt Lirestone into Smooth Lirestone");
                check(ChorusFlowers.flower(Direction.UP, 0, RandomSource.create(1)).canSurvive(level, smoothLirePos.above()), "Smooth Lirestone must support chorus flowers");

                BlockPos coralPos = pos.offset(8, 0, 0);
                var coral = NaturalityBlocks.ECHO_CORAL.defaultBlockState();
                level.setBlockAndUpdate(coralPos, coral);
                check(coral.getDestroySpeed(level, coralPos) == Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState().getDestroySpeed(level, coralPos)
                    && coral.getBlock().getExplosionResistance() == Blocks.BROWN_MUSHROOM_BLOCK.getExplosionResistance(), "Echo Coral must match mushroom block strength");
                check(coral.is(BlockTags.MINEABLE_WITH_AXE) && new ItemStack(Items.WOODEN_AXE).isCorrectToolForDrops(coral), "Echo Coral axe mining incorrect");
                check(coral.getSoundType() == naturality.NaturalitySounds.ECHO_CORAL
                    && coral.getSoundType().getBreakSound() == naturality.NaturalitySounds.ECHO_CORAL_BREAK
                    && coral.getSoundType().getPlaceSound() == naturality.NaturalitySounds.ECHO_CORAL_PLACE
                    && coral.getSoundType().getStepSound() == naturality.NaturalitySounds.ECHO_CORAL_STEP,
                    "Echo Coral custom sounds missing");
                check(NaturalityBlocks.ECHO_CORAL.asItem() == NaturalityBlocks.ECHO_CORAL_ITEM, "Echo Coral block item mapping missing");
                var coralDrops = Block.getDrops(coral, level, coralPos, null, level.players().getFirst(), ItemStack.EMPTY);
                check(coralDrops.size() == 1 && coralDrops.getFirst().is(NaturalityBlocks.ECHO_CORAL_ITEM), "Echo Coral must drop itself without a tool");

                BlockPos heathPos = pos.offset(20, 0, 0);
                var heath = NaturalityBlocks.LYREN_HEATH.defaultBlockState();
                level.setBlockAndUpdate(heathPos, heath);
                check(heath.getDestroySpeed(level, heathPos) == 2.1F
                    && heath.getBlock().getExplosionResistance() == Blocks.END_STONE.getExplosionResistance()
                    && heath.getSoundType() == NaturalityBlocks.LYREN_HEATH_SOUNDS,
                    "Lyren Heath mining strength/sound incorrect");
                check(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(heath), "Wooden pickaxe must harvest Lyren Heath");
                var heathDrops = Block.getDrops(heath, level, heathPos, null, level.players().getFirst(), new ItemStack(Items.WOODEN_PICKAXE));
                check(heathDrops.size() == 1 && heathDrops.getFirst().is(Items.END_STONE), "Lyren Heath must drop End Stone");
                var silkHeathDrops = Block.getDrops(heath, level, heathPos, null, level.players().getFirst(), silkTool);
                check(silkHeathDrops.size() == 1 && silkHeathDrops.getFirst().is(NaturalityBlocks.LYREN_HEATH_ITEM), "Silk Touch must collect Lyren Heath");
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) level.setBlockAndUpdate(heathPos.offset(dx, 0, dz), Blocks.END_STONE.defaultBlockState());
                }
                BlockPos smoothProbe = heathPos.offset(1, 0, 1);
                BlockPos coveredProbe = heathPos.offset(-1, 0, -1);
                BlockPos wetProbe = heathPos.offset(-1, 0, 1);
                level.setBlockAndUpdate(smoothProbe, NaturalityBlocks.SMOOTH_ENDSTONE.defaultBlockState());
                level.setBlockAndUpdate(coveredProbe.above(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(wetProbe.above(), Blocks.WATER.defaultBlockState());
                BlockPos upperProbe = heathPos.offset(1, 1, -1);
                BlockPos lowerProbe = heathPos.offset(0, -1, 1);
                level.setBlockAndUpdate(upperProbe, Blocks.END_STONE.defaultBlockState());
                level.setBlockAndUpdate(lowerProbe, Blocks.END_STONE.defaultBlockState());
                level.setBlockAndUpdate(lowerProbe.above(), Blocks.AIR.defaultBlockState());
                var heathRandom = RandomSource.create(1234);
                for (int tick = 0; tick < 3000; tick++) heath.randomTick(level, heathPos, heathRandom);
                check(level.getBlockState(heathPos.east()).is(Blocks.END_STONE), "Lyren Heath must not spread to exposed End Stone");
                check(level.getBlockState(upperProbe).is(Blocks.END_STONE) && level.getBlockState(lowerProbe).is(Blocks.END_STONE), "Lyren Heath must not spread up or down");
                check(level.getBlockState(smoothProbe).is(NaturalityBlocks.SMOOTH_ENDSTONE), "Lyren Heath must not convert Smooth End Stone");
                check(level.getBlockState(coveredProbe).is(Blocks.END_STONE) && level.getBlockState(wetProbe).is(Blocks.END_STONE), "Lyren Heath must not spread beneath solid cover or water");
                check(heath.getSoundType().getStepSound() == Blocks.WARPED_NYLIUM.defaultBlockState().getSoundType().getStepSound()
                    && heath.getSoundType().getBreakSound() == net.minecraft.world.level.block.SoundType.STONE.getBreakSound(), "Lyren Heath must combine nylium footsteps with stone break sounds");
                level.setBlockAndUpdate(heathPos.above(), Blocks.STONE.defaultBlockState());
                check(level.getBlockState(heathPos).is(NaturalityBlocks.LYREN_HEATH), "Lyren Heath must wait for a random tick before decay");
                level.setBlockAndUpdate(heathPos.east(), Blocks.END_STONE.defaultBlockState());
                for (int tick = 0; tick < 3000; tick++) heath.randomTick(level, heathPos, heathRandom);
                check(level.getBlockState(heathPos.east()).is(Blocks.END_STONE), "Covered Lyren Heath must not spread");

                check(level.getBlockState(heathPos).is(Blocks.END_STONE), "Fully covered Lyren Heath must decay to End Stone");
                level.setBlockAndUpdate(heathPos.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(heathPos, heath);
                BlockPos treeBase = new BlockPos(160, 100, 0);
                for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                    level.setBlockAndUpdate(treeBase.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(treeBase.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                for (int y = 0; y < 4; y++) level.setBlockAndUpdate(treeBase.above(y), Blocks.OAK_LOG.defaultBlockState());
                level.setBlockAndUpdate(treeBase.above(4), Blocks.OAK_LEAVES.defaultBlockState());
                var mossFeature = new naturality.worldgen.TreeMossFeature();
                check(mossFeature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(42), treeBase.east(3)), "Tree moss must generate around a supported forest tree");
                int groundMoss = 0, trunkMoss = 0;
                for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                    var mossGround = level.getBlockState(treeBase.offset(x, 0, z));
                    if (mossGround.is(NaturalityBlocks.MOSS) && mossGround.getValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(Direction.DOWN))) groundMoss++;
                }
                for (int y = 0; y < 4; y++) for (Direction direction : Direction.Plane.HORIZONTAL) {
                    var coating = level.getBlockState(treeBase.above(y).relative(direction));
                    if (coating.is(NaturalityBlocks.MOSS) && coating.getValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(direction.getOpposite()))) trunkMoss++;
                }
                check(groundMoss >= 24 && groundMoss <= 48 && trunkMoss >= 2, "Tree moss must coat both trunk and a limited ground patch");
                var mossRemaining = new java.util.HashSet<BlockPos>();
                for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y = 0; y < 4; y++) {
                    var mossCheck = treeBase.offset(x, y, z);
                    if (level.getBlockState(mossCheck).is(NaturalityBlocks.MOSS)) mossRemaining.add(mossCheck);
                }
                var mossQueue = new java.util.ArrayDeque<BlockPos>();
                var mossFirst = mossRemaining.iterator().next();
                mossRemaining.remove(mossFirst); mossQueue.add(mossFirst);
                while (!mossQueue.isEmpty()) {
                    var current = mossQueue.removeFirst();
                    for (Direction direction : Direction.values()) {
                        var next = current.relative(direction);
                        if (mossRemaining.remove(next)) mossQueue.add(next);
                    }
                }
                check(mossRemaining.isEmpty(), "Generated moss ground and trunk must form one connected patch");
                check(naturality.worldgen.TreeMossFeature.rarity(net.minecraft.world.level.biome.Biomes.OLD_GROWTH_SPRUCE_TAIGA) == 1
                    && naturality.worldgen.TreeMossFeature.rarity(net.minecraft.world.level.biome.Biomes.DARK_FOREST) == 4
                    && naturality.worldgen.TreeMossFeature.rarity(net.minecraft.world.level.biome.Biomes.BIRCH_FOREST) == 16, "Tree moss biome frequency tiers incorrect");
                for (var excluded : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, net.minecraft.world.level.biome.Biomes.DESERT,
                        net.minecraft.world.level.biome.Biomes.JUNGLE, net.minecraft.world.level.biome.Biomes.SNOWY_TAIGA, net.minecraft.world.level.biome.Biomes.PALE_GARDEN))
                    check(naturality.worldgen.TreeMossFeature.rarity(excluded) == 0, "Excluded biome must not generate tree moss");
                check(!mossFeature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(42), treeBase.offset(32, 0, 0)), "Moss must not generate in an unlisted biome");

                BlockPos mossPos = pos.offset(40, 0, 0);
                for (Direction face : Direction.values()) {
                    var faceProperty = net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(face);
                    var moss = NaturalityBlocks.MOSS.defaultBlockState().setValue(faceProperty, true);
                    var lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(faceProperty, true);
                    level.setBlockAndUpdate(mossPos.relative(face), Blocks.STONE.defaultBlockState());
                    check(moss.canSurvive(level, mossPos), "Moss must attach to a solid surface on " + face);
                    check(moss.getShape(level, mossPos).bounds().equals(lichen.getShape(level, mossPos).bounds()), "Moss shape differs from lichen on " + face);
                    check(moss.getDestroySpeed(level, mossPos) == lichen.getDestroySpeed(level, mossPos)
                        && moss.getSoundType() == Blocks.MOSS_BLOCK.defaultBlockState().getSoundType()
                        && moss.getLightEmission() == 0, "Moss strength/sounds/light incorrect");
                    level.setBlockAndUpdate(mossPos, moss);
                    var mossDrops = Block.getDrops(moss, level, mossPos, null, level.players().getFirst(), new ItemStack(Items.SHEARS));
                    check(mossDrops.size() == 1 && mossDrops.getFirst().is(NaturalityBlocks.MOSS_ITEM) && mossDrops.getFirst().getCount() == 1, "Shears must collect one Moss per face");
                    level.setBlockAndUpdate(mossPos.relative(face), Blocks.AIR.defaultBlockState());
                    check(!moss.canSurvive(level, mossPos) && level.getBlockState(mossPos).isAir(), "Unsupported Moss must break");
                }

                BlockPos cloverPos = pos.offset(50, 0, 0);
                var clovers = NaturalityBlocks.CLOVERS.defaultBlockState();
                var wildflowers = Blocks.WILDFLOWERS.defaultBlockState();
                for (var support : new Block[]{Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.END_STONE, Blocks.STONE}) {
                    level.setBlockAndUpdate(cloverPos.below(), support.defaultBlockState());
                    check(clovers.canSurvive(level, cloverPos) == wildflowers.canSurvive(level, cloverPos), "Clovers support differs from wildflowers");
                }
                level.setBlockAndUpdate(cloverPos.below(), Blocks.DIRT.defaultBlockState());
                check(clovers.getSoundType() == wildflowers.getSoundType()
                    && clovers.getDestroySpeed(level, cloverPos) == wildflowers.getDestroySpeed(level, cloverPos), "Clovers properties differ from wildflowers");
                for (int amount = 1; amount <= 4; amount++) {
                    var cloverState = clovers.setValue(net.minecraft.world.level.block.FlowerBedBlock.AMOUNT, amount);
                    var flowerState = wildflowers.setValue(net.minecraft.world.level.block.FlowerBedBlock.AMOUNT, amount);
                    check(cloverState.getShape(level, cloverPos).bounds().equals(flowerState.getShape(level, cloverPos).bounds()), "Clovers shape differs");
                    level.setBlockAndUpdate(cloverPos, cloverState);
                    var cloverDrops = Block.getDrops(cloverState, level, cloverPos, null, level.players().getFirst(), silkTool);
                    check(cloverDrops.size() == 1 && cloverDrops.getFirst().is(NaturalityBlocks.CLOVERS_ITEM) && cloverDrops.getFirst().getCount() == amount, "Clovers drops must match amount");
                }
                BlockPos groupCenter = pos.offset(96, 0, 0);
                for (int seed = 0; seed < 8; seed++) {
                    for (int x = -18; x <= 18; x++) for (int z = -18; z <= 18; z++) {
                        level.setBlockAndUpdate(groupCenter.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                        level.setBlockAndUpdate(groupCenter.offset(x, 0, z), Blocks.AIR.defaultBlockState());
                    }
                    check(new naturality.worldgen.CloverPatchesFeature().place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), groupCenter), "Flat supported ground must generate a clover group");
                    var pendingClovers = new java.util.HashSet<BlockPos>();
                    for (int x = -18; x <= 18; x++) for (int z = -18; z <= 18; z++) {
                        var probePos = groupCenter.offset(x, 0, z);
                        if (level.getBlockState(probePos).is(NaturalityBlocks.CLOVERS)) pendingClovers.add(probePos);
                    }
                    var patchSizes = new java.util.ArrayList<Integer>();
                    while (!pendingClovers.isEmpty()) {
                        var queue = new java.util.ArrayDeque<BlockPos>();
                        var first = pendingClovers.iterator().next();
                        pendingClovers.remove(first); queue.add(first);
                        int size = 0;
                        while (!queue.isEmpty()) {
                            var current = queue.removeFirst(); size++;
                            for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                                var next = current.relative(direction);
                                if (pendingClovers.remove(next)) queue.add(next);
                            }
                        }
                        patchSizes.add(size);
                    }
                    patchSizes.sort(java.util.Comparator.reverseOrder());
                    check(patchSizes.size() >= 2 && patchSizes.size() <= 4, "Clovers must generate 2-4 separated patches");
                    check(patchSizes.getFirst() >= 8 && patchSizes.getFirst() > patchSizes.get(1), "Clovers must have one dominant patch");
                    check(patchSizes.stream().mapToInt(Integer::intValue).sum() <= 38, "Clovers group must remain small");
                }

                int rareClovers = 0;
                level.setBlockAndUpdate(cloverPos, clovers);
                for (int roll = 0; roll < 5000; roll++) {
                    var result = Block.getDrops(clovers, level, cloverPos, null, level.players().getFirst(), ItemStack.EMPTY);
                    check(result.size() == 1, "Clovers must return either normal or rare item");
                    if (result.getFirst().is(naturality.NaturalityItems.FOUR_LEAF_CLOVER)) rareClovers++;
                    else check(result.getFirst().is(NaturalityBlocks.CLOVERS_ITEM), "Unexpected Clovers drop");
                }
                check(rareClovers > 10 && rareClovers < 100, "Rare Clovers drop rate must be approximately 1%");
                BlockPos brewingPos = cloverPos.offset(0, 0, 3);
                level.setBlockAndUpdate(brewingPos, Blocks.BREWING_STAND.defaultBlockState());
                var brewing = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity)level.getBlockEntity(brewingPos);
                var bottleItems = new net.minecraft.world.item.Item[]{Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION};
                for (int slot = 0; slot < 3; slot++) brewing.setItem(slot,
                    net.minecraft.world.item.alchemy.PotionContents.createItemStack(bottleItems[slot], net.minecraft.world.item.alchemy.Potions.AWKWARD));
                brewing.setItem(3, new ItemStack(naturality.NaturalityItems.FOUR_LEAF_CLOVER));
                brewing.setItem(4, new ItemStack(Items.BLAZE_POWDER));
                for (int tick = 0; tick < 405; tick++) net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(level, brewingPos, level.getBlockState(brewingPos), brewing);
                for (int slot = 0; slot < 3; slot++) check(brewing.getItem(slot).is(bottleItems[slot])
                    && brewing.getItem(slot).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(net.minecraft.world.item.alchemy.Potions.LUCK), "Four Leaf Clover must brew Luck in every bottle type");
                brewing.setItem(3, new ItemStack(Items.GUNPOWDER));
                for (int tick = 0; tick < 405; tick++) net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(level, brewingPos, level.getBlockState(brewingPos), brewing);
                check(brewing.getItem(0).is(Items.SPLASH_POTION), "Gunpowder must turn Luck into splash Luck");
                brewing.setItem(3, new ItemStack(Items.DRAGON_BREATH));
                for (int tick = 0; tick < 405; tick++) net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(level, brewingPos, level.getBlockState(brewingPos), brewing);
                check(brewing.getItem(0).is(Items.LINGERING_POTION)
                    && brewing.getItem(0).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(net.minecraft.world.item.alchemy.Potions.LUCK), "Dragon breath must turn splash Luck into lingering Luck");

                level.setBlockAndUpdate(cloverPos, clovers);
                ((net.minecraft.world.level.block.FlowerBedBlock)NaturalityBlocks.CLOVERS).performBonemeal(level, RandomSource.create(1), cloverPos, clovers, net.minecraft.world.level.block.BonemealSource.INTERACTION);
                check(level.getBlockState(cloverPos).getValue(net.minecraft.world.level.block.FlowerBedBlock.AMOUNT) == 2, "Bonemeal must increase Clovers amount");

                var sprout = NaturalityBlocks.CHORUS_SPROUT.defaultBlockState();
                for(var lyrenBlock:java.util.List.of(NaturalityBlocks.LYREN_SPROUT,NaturalityBlocks.LYREN_GROWTH)) {
                    var lyren=lyrenBlock.defaultBlockState();
                    var plantPos=heathPos.above();
                    check(lyren.getSoundType()==sprout.getSoundType()
                        && lyren.getDestroySpeed(level,plantPos)==sprout.getDestroySpeed(level,plantPos)
                        && lyren.getCollisionShape(level,plantPos).isEmpty(),"Lyren plant properties differ from Chorus Sprout");
                    check(lyren.getShape(level,plantPos).bounds().equals(sprout.getShape(level,plantPos).bounds()),"Lyren plant outline differs");
                    for(var substrate:java.util.List.of(Blocks.END_STONE,NaturalityBlocks.LIRESTONE,NaturalityBlocks.LYREN_HEATH,
                        NaturalityBlocks.SMOOTH_ENDSTONE,Blocks.DIRT)) {
                        level.setBlockAndUpdate(heathPos,substrate.defaultBlockState());
                        check(lyren.canSurvive(level,plantPos)==sprout.canSurvive(level,plantPos),"Lyren plant support differs");
                    }
                    var bareDrops=Block.getDrops(lyren,level,plantPos,null,level.players().getFirst(),ItemStack.EMPTY);
                    var sheared=Block.getDrops(lyren,level,plantPos,null,level.players().getFirst(),new ItemStack(Items.SHEARS));
                    check(bareDrops.isEmpty() && sheared.size()==1 && sheared.getFirst().is(lyrenBlock.asItem()),"Lyren plant shearing loot incorrect");
                }
                level.setBlockAndUpdate(heathPos,heath);
                check(sprout.canSurvive(level, heathPos.above()), "Chorus Sprout must survive on Lyren Heath");
                level.setBlockAndUpdate(heathPos.above(), sprout);
                heath.randomTick(level, heathPos, heathRandom);
                check(level.getBlockState(heathPos).is(NaturalityBlocks.LYREN_HEATH), "Chorus Sprout must not cause Lyren Heath decay");
                level.setBlockAndUpdate(heathPos.above(), Blocks.STONE_SLAB.defaultBlockState());
                heath.randomTick(level, heathPos, heathRandom);
                check(level.getBlockState(heathPos).is(NaturalityBlocks.LYREN_HEATH), "Partial blocks must not cause full-cover decay");
                check(sprout.getSoundType() == Blocks.WARPED_ROOTS.defaultBlockState().getSoundType(), "Chorus Sprout must use warped roots sounds");
                check(Blocks.CHORUS_FLOWER.defaultBlockState().getSoundType() == Blocks.WARPED_ROOTS.defaultBlockState().getSoundType(), "Chorus Flower must use warped roots sounds");
                check(Blocks.CHORUS_PLANT.defaultBlockState().getSoundType() == Blocks.WARPED_STEM.defaultBlockState().getSoundType(), "Chorus Plant must use warped stem sounds");
                check(sprout.canSurvive(level, lirestonePos.above()), "Chorus Sprout must survive on Lirestone");
                BlockPos sproutBase = pos.offset(4, 0, 0);
                level.setBlockAndUpdate(sproutBase, Blocks.END_STONE.defaultBlockState());
                check(sprout.canSurvive(level, sproutBase.above()), "Chorus Sprout must survive on End Stone");
                check(sprout.getDestroySpeed(level, sproutBase.above()) == 0.0F
                    && sprout.getCollisionShape(level, sproutBase.above()).isEmpty(), "Chorus Sprout must be instant-breaking and non-colliding");
                level.setBlockAndUpdate(sproutBase.above(), sprout);
                var sproutDrops = Block.getDrops(sprout, level, sproutBase.above(), null, level.players().getFirst(), new ItemStack(Items.SHEARS));
                check(sproutDrops.size() == 1 && sproutDrops.getFirst().is(NaturalityBlocks.CHORUS_SPROUT_ITEM), "Shears must collect Chorus Sprout");
                check(Block.getDrops(sprout, level, sproutBase.above(), null, level.players().getFirst(), ItemStack.EMPTY).isEmpty(), "Ordinary breaking must not drop Chorus Sprout");
                level.setBlockAndUpdate(sproutBase, Blocks.DIRT.defaultBlockState());
                check(level.getBlockState(sproutBase.above()).isAir() && !sprout.canSurvive(level, sproutBase.above()), "Chorus Sprout must break when its End substrate is removed");

                var end = s.getLevel(Level.END);
                var island = end.getChunk(2, 0);
                int regular = 0;
                int smooth = 0;
                var probe = new BlockPos.MutableBlockPos();
                for (int x = 32; x < 48; x++) for (int z = 0; z < 16; z++) {
                    int depth = 0;
                    for (int y = end.getMaxY(); y >= end.getMinY(); y--) {
                        probe.set(x, y, z);
                        var material = island.getBlockState(probe);
                        if (!naturality.worldgen.LirestoneBoulderFeature.isEndStone(material)) {
                            depth = 0;
                            continue;
                        }
                        depth++;
                        if (material.is(NaturalityBlocks.LIRESTONE) || material.is(NaturalityBlocks.SMOOTH_LIRESTONE)) continue;
                        check(material.is(depth <= 2 ? Blocks.END_STONE : NaturalityBlocks.SMOOTH_ENDSTONE),
                            "Incorrect initial End island layer at " + probe + ", depth " + depth);
                        if (depth <= 2) regular++; else smooth++;
                    }
                }
                check(regular > 100 && smooth > regular, "Initial End island did not receive a smooth interior");

                BlockPos small = new BlockPos(0, 160, 0);
                new net.minecraft.world.level.levelgen.feature.EndIslandFeature().place(end,
                    end.getChunkSource().getGenerator(), RandomSource.create(42), small);
                int smallSmooth = 0;
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                    int depth = 0;
                    for (int y = 160; y >= 144; y--) {
                        probe.set(x, y, z);
                        var material = end.getBlockState(probe);
                        if (!material.is(Blocks.END_STONE) && !material.is(NaturalityBlocks.SMOOTH_ENDSTONE)) {
                            depth = 0;
                            continue;
                        }
                        check(material.is(++depth <= 2 ? Blocks.END_STONE : NaturalityBlocks.SMOOTH_ENDSTONE),
                            "Incorrect small End island layer at " + probe);
                        if (depth > 2) smallSmooth++;
                    }
                }
                check(smallSmooth > 0, "Small outer-island feature lacks smooth lower layers");
                for (BlockPos outer : java.util.List.of(new BlockPos(1152, 200, 0), new BlockPos(-2048, 200, -2048)))
                    check(!new net.minecraft.world.level.levelgen.feature.EndIslandFeature().place(end,
                        end.getChunkSource().getGenerator(), RandomSource.create(42), outer),
                        "Vanilla disc feature still generates in the outer End");
            });
            context.runOnClient(client -> check(new ItemStack(NaturalityBlocks.SMOOTH_ENDSTONE_ITEM).getHoverName().getString()
                .equals("Smooth End Stone"), "Block name did not load"));
            context.runOnClient(client -> check(new ItemStack(NaturalityBlocks.LIRESTONE_ITEM).getHoverName().getString()
                .equals("Lirestone"), "Lirestone name did not load"));
        }
    }
}
