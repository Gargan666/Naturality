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
                    && lirestone.getSoundType() == vanilla.getSoundType(), "Lirestone hardness/resistance/sound incorrect");
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
                        if (material.is(NaturalityBlocks.LIRESTONE)) continue;
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
