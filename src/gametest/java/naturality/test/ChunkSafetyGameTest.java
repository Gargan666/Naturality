package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;

/** Real worker snapshots at negative coordinates and across vertical section boundaries. */
public final class ChunkSafetyGameTest implements FabricClientGameTest {
    private static final BlockPos[] SAVED_SNOW = {
        new BlockPos(-29, 101, 35), new BlockPos(35, 101, -29),
        new BlockPos(-29, 101, -29), new BlockPos(35, 101, 35)
    };

    @Override public void runTest(ClientGameTestContext context) {
        checkSnowSearchBudget();
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            var server = world.getServer();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a -0.5 101 -0.5");
            server.runCommand("setblock -1 64 -1 gold_block");
            server.runCommand("setblock -1 143 -1 diamond_block");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            server.runOnServer(instance -> {
                var level = instance.overworld();
                check(naturality.util.LoadedChunks.has(level, new BlockPos(-1, 64, -1)),
                    "Server availability recognizes an already loaded chunk");
                int loaded = level.getChunkSource().getLoadedChunksCount();
                check(!naturality.util.LoadedChunks.has(level, new BlockPos(1000000, 100, 1000000)),
                    "Server availability does not request absent chunks");
                check(level.getChunkSource().getLoadedChunksCount() == loaded,
                    "Availability leaves the server loaded-chunk count unchanged");
                check(naturality.config.GameplaySettings.snowWrapping(level), "Snow wrapping is enabled for reload regression");
                for (var pos : SAVED_SNOW) {
                    level.setBlockAndUpdate(pos.below(2), Blocks.GRASS_BLOCK.defaultBlockState());
                    level.setBlockAndUpdate(pos.below(), Blocks.FERN.defaultBlockState());
                    level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
                    // An ordinary full snow layer in the neighboring column must
                    // retain its own skylight boundary after the fitted-snow repair.
                    level.setBlockAndUpdate(pos.east().below(2), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(pos.east().below(), Blocks.SNOW.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 8));
                }
            });
            context.runOnClient(client -> {
                var level = java.util.Objects.requireNonNull(client.level);
                var region = new RenderRegionCache().createRegion(level, SectionPos.asLong(-1, 6, -1));
                check(region.getBlockState(new BlockPos(-1, 64, -1)).is(Blocks.GOLD_BLOCK),
                    "Downward snow queries use the correct extra section");
                check(region.getBlockState(new BlockPos(-1, 143, -1)).is(Blocks.DIAMOND_BLOCK),
                    "Upward owner queries use the correct extra section");
                for (var pos : new BlockPos[] {new BlockPos(-1, -1024, -1), new BlockPos(-1, 1024, -1),
                        new BlockPos(1024, 96, -1), new BlockPos(-1024, 96, -1)}) {
                    check(region.getBlockState(pos).isAir(), "Out-of-region block queries return air");
                    check(region.getFluidState(pos).isEmpty(), "Out-of-region fluid is empty");
                    check(region.getBlockEntity(pos) == null, "Out-of-region block entity is absent");
                }
                level.setBlock(new BlockPos(-1, 64, -1), Blocks.STONE.defaultBlockState(), 18);
                check(region.getBlockState(new BlockPos(-1, 64, -1)).is(Blocks.GOLD_BLOCK),
                    "Worker snapshot remains stable after world edits");
                check(!naturality.util.LoadedChunks.has(level, new BlockPos(1000000, 100, 1000000)),
                    "Availability check does not load an absent chunk");
            });
        }
        // Reopen actual persisted chunks: newly generated chunks skip SnowLighting's
        // repair callback and cannot catch world/local coordinate mixups there.
        try (var reopened = save.open()) {
            reopened.getConnection().waitForClientboundPackets();
            reopened.getConnection().waitForChunksRender();
            reopened.getServer().runOnServer(instance -> {
                var level = instance.overworld();
                for (var pos : SAVED_SNOW) {
                    check(level.getBlockState(pos).is(Blocks.SNOW), "Fitted snow survived saving and reopening");
                    var sources = level.getChunkAt(pos).getSkyLightSources();
                    check(sources.getLowestSourceY(pos.getX() & 15, pos.getZ() & 15) == 100,
                        "Reloaded fitted snow does not darken its empty owner cell at " + pos);
                    check(sources.getLowestSourceY(pos.east().getX() & 15, pos.getZ() & 15) == 101,
                        "Repair preserves the ordinary snow column next to " + pos);
                }
            });
        }
    }

    private static void checkSnowSearchBudget() {
        int limit = naturality.snow.SnowGeometry.MAX_DEPTH;
        var column = new net.minecraft.world.level.BlockGetter() {
            @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
                int depth = -pos.getY();
                check(depth <= limit, "Stacked snow shares one support-search depth budget");
                return depth < limit ? Blocks.SNOW.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 8)
                    : Blocks.SUGAR_CANE.defaultBlockState();
            }
            @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
                return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            }
            @Override public int getMinY() { return -64; }
            @Override public int getHeight() { return 384; }
        };
        naturality.snow.SnowGeometry.surfaces(column, BlockPos.ZERO);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
