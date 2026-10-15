package naturality.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Generation halo persistence, bounded idle saving and a real shutdown/reopen. */
public final class EndSaveGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean oldSync = context.computeOnClient(client -> client.options.syncWrites);
        context.runOnClient(client -> client.options.syncWrites = true);
        try { checkWorld(context); }
        finally { context.runOnClient(client -> client.options.syncWrites = oldSync); }
    }

    private static void checkWorld(ClientGameTestContext context) {
        var world = context.worldBuilder().create();
        var save = world.getWorldSave();
        var protectedGeneration = new CompletableFuture<Void>();
        var protoPos = new BlockPos[3];
        int[] initial = new int[1];
        boolean patched = java.util.Arrays.stream(ChunkMap.class.getDeclaredMethods())
            .anyMatch(m -> m.getName().contains("checkpointEndHalo"));
        try {
            var server = world.getServer();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("execute in minecraft:the_end run forceload add 3200 3712 3312 3824");
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                for (int z = 232; z < 240; z++) for (int x = 200; x < 208; x++) end.getChunk(x, z);
                end.setBlockAndUpdate(new BlockPos(3200, 180, 3712), Blocks.DIAMOND_BLOCK.defaultBlockState());
            });
            context.waitTicks(40);
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                var map = end.getChunkSource().chunkMap;
                initial[0] = dirtyProtos(map);
                check(initial[0] > 500, "Fixture must contain a substantial unsaved generation halo");
                var holder = holders(map).stream().filter(h -> h.getLatestChunk() instanceof ProtoChunk
                    && h.getLatestChunk().getPersistedStatus() == ChunkStatus.INITIALIZE_LIGHT).findFirst().orElseThrow();
                var chunk = holder.getLatestChunk();
                protoPos[0] = new BlockPos(chunk.getPos().getMinBlockX(), 180, chunk.getPos().getMinBlockZ());
                chunk.setBlockState(protoPos[0], Blocks.EMERALD_BLOCK.defaultBlockState());
                chunk.markUnsaved();
                var checkpointed = holders(map).stream().filter(h -> h != holder && h.getLatestChunk() instanceof ProtoChunk
                    && h.getLatestChunk().getPersistedStatus() == ChunkStatus.INITIALIZE_LIGHT).findFirst().orElseThrow().getLatestChunk();
                protoPos[1] = new BlockPos(checkpointed.getPos().getMinBlockX(), 180, checkpointed.getPos().getMinBlockZ());
                checkpointed.setBlockState(protoPos[1], Blocks.LAPIS_BLOCK.defaultBlockState());
                checkpointed.markUnsaved();
                try {
                    var method = ChunkHolder.class.getDeclaredMethod("addSaveDependency", CompletableFuture.class);
                    method.setAccessible(true);
                    method.invoke(holder, protectedGeneration);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                idleSave(map, false);
                check(dirtyProtos(map) == initial[0], "No checkpoint without spare tick time");
                end.noSave = true;
                try { idleSave(map, true); }
                finally { end.noSave = false; }
                check(dirtyProtos(map) == initial[0], "Respect save-off");
                System.out.println("End save probe initial dirty proto chunks=" + initial[0]);
            });
            // Fabric's lockstep client/server phases consume the normal spare-time
            // budget. Supply an explicit idle window, identically in both variants.
            for (int tick = 0; tick < 2400; tick++) {
                if (patched && server.computeOnServer(s -> dirtyProtos(s.getLevel(Level.END).getChunkSource().chunkMap)) == 1) break;
                context.waitTick();
                server.runOnServer(s -> {
                    var map = s.getLevel(Level.END).getChunkSource().chunkMap;
                    int before = dirtyProtos(map);
                    idleSave(map, true);
                    check(before - dirtyProtos(map) <= 2, "At most two proto snapshots per idle call");
                });
            }
            server.runOnServer(s -> {
                var map = s.getLevel(Level.END).getChunkSource().chunkMap;
                int dirty = dirtyProtos(map);
                System.out.println("End save probe settled dirty proto chunks=" + dirty);
                System.out.println("End save remaining statuses=" + holders(map).stream()
                    .filter(h -> h.getLatestChunk() instanceof ProtoChunk && h.getLatestChunk().isUnsaved())
                    .collect(java.util.stream.Collectors.groupingBy(h -> h.getLatestChunk().getPersistedStatus()
                        + ":ready=" + h.isReadyForSaving(), java.util.stream.Collectors.counting())));
                if (patched) check(dirty == 1, "Only the chunk with unfinished generation may remain unsaved: " + dirty);
                check(s.getLevel(Level.END).getChunkSource().getChunk(protoPos[0].getX() >> 4,
                    protoPos[0].getZ() >> 4, ChunkStatus.INITIALIZE_LIGHT, false).isUnsaved(),
                    "Never snapshot a chunk with pending generation dependencies");
                if (patched) {
                    var reusable = holders(map).stream().filter(h -> h.getLatestChunk() instanceof ProtoChunk
                        && h.getLatestChunk().getPersistedStatus() == ChunkStatus.INITIALIZE_LIGHT
                        && !h.getLatestChunk().isUnsaved() && !h.getPos().equals(new net.minecraft.world.level.ChunkPos(protoPos[1].getX() >> 4, protoPos[1].getZ() >> 4)))
                        .findFirst().orElseThrow();
                    var acquired = map.acquireGeneration(reusable.getPos().pack());
                    try {
                        var chunk = acquired.getLatestChunk();
                        check(chunk.isUnsaved(), "Generation reuse must dirty a checkpoint before neighbor decoration");
                        protoPos[2] = new BlockPos(chunk.getPos().getMinBlockX(), 180, chunk.getPos().getMinBlockZ());
                        chunk.setBlockState(protoPos[2], Blocks.GOLD_BLOCK.defaultBlockState());
                    } finally { map.releaseGeneration(acquired); }
                }
                protectedGeneration.complete(null);
            });
        } finally {
            protectedGeneration.complete(null);
            long start = System.nanoTime();
            world.close();
            System.out.println("End save probe close_ms=" + (System.nanoTime() - start) / 1e6);
        }
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(s -> {
                var end = s.getLevel(Level.END);
                check(end.getBlockState(new BlockPos(3200, 180, 3712)).is(Blocks.DIAMOND_BLOCK),
                    "Full chunk edit survives save/reopen");
                var chunk = end.getChunkSource().getChunk(protoPos[0].getX() >> 4, protoPos[0].getZ() >> 4,
                    ChunkStatus.INITIALIZE_LIGHT, true);
                check(chunk.getBlockState(protoPos[0]).is(Blocks.EMERALD_BLOCK),
                    "Final shutdown still saves a previously busy proto chunk");
                var checkpointed = end.getChunkSource().getChunk(protoPos[1].getX() >> 4, protoPos[1].getZ() >> 4,
                    ChunkStatus.INITIALIZE_LIGHT, true);
                check(checkpointed.getBlockState(protoPos[1]).is(Blocks.LAPIS_BLOCK),
                    "Background checkpoint persists partial terrain across reopen");
                if (patched) {
                    var resumed = end.getChunkSource().getChunk(protoPos[2].getX() >> 4, protoPos[2].getZ() >> 4,
                        ChunkStatus.INITIALIZE_LIGHT, true);
                    check(resumed.getBlockState(protoPos[2]).is(Blocks.GOLD_BLOCK),
                        "Decoration after a checkpoint survives shutdown even during the save cooldown");
                }
            });
        }
    }

    private static void idleSave(ChunkMap map, boolean haveTime) {
        try {
            var method = ChunkMap.class.getDeclaredMethod("tick", BooleanSupplier.class);
            method.setAccessible(true);
            method.invoke(map, (BooleanSupplier)() -> haveTime);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static List<ChunkHolder> holders(ChunkMap map) {
        try {
            var field = ChunkMap.class.getDeclaredField("visibleChunkMap");
            field.setAccessible(true);
            var values = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>)field.get(map);
            var result = new ArrayList<ChunkHolder>();
            for (var value : values.values()) result.add((ChunkHolder)value);
            return result;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static int dirtyProtos(ChunkMap map) {
        int dirty = 0;
        for (var holder : holders(map)) {
            var chunk = holder.getLatestChunk();
            if (chunk instanceof ProtoChunk && !(chunk instanceof ImposterProtoChunk) && chunk.isUnsaved()) dirty++;
        }
        return dirty;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
