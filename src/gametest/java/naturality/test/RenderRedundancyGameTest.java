package naturality.test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Random;
import naturality.client.sky.shared.clouds.CloudLayerRenderer;
import naturality.client.fluids.shared.FallingFluidTail.FluidSectionScan;
import naturality.client.rendering.lighting.dynamic.DynamicLighting;
import naturality.client.blocks.snow.SurfaceSnowModel.SnowRenderRegion;
import naturality.config.live.NaturalityConfig;
import naturality.test.reference.ReferenceCloudLayerRenderer;
import naturality.test.reference.ReferenceWindShelter;
import naturality.systems.weather.wind.WindPush.WindShapes;
import naturality.systems.weather.wind.WindPush.WindShelter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Checks removal of invisible/redundant work without reducing feature coverage. */
public final class RenderRedundancyGameTest implements FabricClientGameTest {
    @SuppressWarnings("unused") // Volatile benchmark sink prevents dead-code elimination; deliberately never read.
    private static volatile Object sink;
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 112 0 0 60");
            server.runCommand("fill -16 99 -16 16 99 16 stone");
            server.runCommand("fill -8 103 -8 8 103 8 stone");
            server.runCommand("fill -4 100 -4 -4 102 4 oak_leaves[persistent=true]");
            server.runCommand("fill 4 100 -4 4 100 4 stone_slab[type=bottom]");
            server.runCommand("fill 2 100 -4 2 101 4 oak_fence");
            world.getConnection().waitForClientboundPackets();
            checkClouds(context);
            context.runOnClient(client -> {
                checkShelter(client.level);
                checkSnapshots(client.level);
                checkFluids(client.level);
                checkWindUniform();
                DynamicLighting.tick(client);
                Field pause = field(client.getClass(), "pause"), ticks = field(DynamicLighting.class, "ticks");
                boolean wasPaused = pause.getBoolean(client);
                int before = ticks.getInt(null);
                try {
                    pause.setBoolean(client, true);
                    if (!client.isPaused()) throw new AssertionError("Pause fixture is not active");
                    for (int i = 0; i < 40; i++) DynamicLighting.tick(client);
                    if (ticks.getInt(null) != before) throw new AssertionError("Dynamic light clock advanced while paused");
                } finally { pause.setBoolean(client, wasPaused); }
                DynamicLighting.tick(client);
                if (!wasPaused && ticks.getInt(null) != before + 1) throw new AssertionError("Dynamic lighting did not resume");
                System.out.println("RENDER_REDUNDANCY: shelter equivalence, snapshot invalidation, fluid scans and pause checks passed");
            });
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void checkShelter(ClientLevel level) {
        Random random = new Random(24389);
        for (int i = 0; i < 512; i++) {
            BlockPos pos = new BlockPos(random.nextInt(25)-12, 99+random.nextInt(7), random.nextInt(25)-12);
            // Identical fixed light input isolates the geometric probe, including roof edges.
            boolean old = WindShapes.raw(() -> ReferenceWindShelter.hasOpenSkyPath(level, pos, _ -> 15));
            boolean now = WindShapes.raw(() -> WindShelter.hasOpenSkyPath(level, pos, _ -> 15));
            if (old != now) throw new AssertionError("Shelter changed at " + pos);
        }
        var point = new BlockPos(0, 100, 0);
        for (int round = 0; round < 4; round++) {
            probe("shelter-original", 10000, () -> sink = WindShapes.raw(() -> ReferenceWindShelter.hasOpenSkyPath(level, point, _ -> 15)));
            probe("shelter-optimized", 10000, () -> sink = WindShapes.raw(() -> WindShelter.hasOpenSkyPath(level, point, _ -> 15)));
        }
    }

    private static void checkSnapshots(ClientLevel level) throws ReflectiveOperationException {
        long node = SectionPos.asLong(0, 6, 0);
        var region = new RenderRegionCache().createRegion(level, node);
        if (((SnowRenderRegion)region).naturality$mayHaveSnow()) throw new AssertionError("Snow-free region not recognized");
        var extra = field(region.getClass(), "naturality$snowSections");
        if (!((java.util.Map<?, ?>)extra.get(region)).isEmpty()) throw new AssertionError("Snow-free region copied distant sections");
        // Stalk root searches also read the extended lower snapshot. Even a
        // player-built stalk spanning several sections must retain its anchor.
        for (int y = 60; y <= 103; y++) level.setBlock(new BlockPos(12,y,12), Blocks.SUGAR_CANE.defaultBlockState(), 2);
        var stalk = new RenderRegionCache().createRegion(level, node);
        var tip = new BlockPos(12,103,12);
        if (((SnowRenderRegion)stalk).naturality$mayHaveSnow()
                || naturality.client.systems.weather.wind.WindRendering.vertexTag(stalk,tip,Blocks.SUGAR_CANE.defaultBlockState(),.5F,0,.5F) != 43)
            throw new AssertionError("Snow-free snapshot lost a tall stalk's wind anchor");
        // A neighboring owner two sections up still needs the full support snapshot.
        BlockPos owner = new BlockPos(16, 132, 0), support = owner.below();
        level.setBlock(support, Blocks.OAK_LEAVES.defaultBlockState(), 3);
        level.setBlock(owner, Blocks.SNOW.defaultBlockState(), 3);
        var snowy = new RenderRegionCache().createRegion(level, node);
        if (!((SnowRenderRegion)snowy).naturality$mayHaveSnow() || !snowy.getBlockState(owner).is(Blocks.SNOW))
            throw new AssertionError("Distant snow owner missing from worker snapshot");
        level.setBlock(owner, Blocks.AIR.defaultBlockState(), 3);
        if (!snowy.getBlockState(owner).is(Blocks.SNOW)) throw new AssertionError("Worker snapshot read live edits");
        // Palette presence may conservatively retain removed states until compaction.
        if (!region.getBlockState(owner).isAir()) throw new AssertionError("Snow-free snapshot changed after creation");
        System.out.println("SNOW_SNAPSHOTS: snow-free=0 extra, snowy=" + ((java.util.Map<?, ?>)extra.get(snowy)).size());
    }

    private static void checkFluids(ClientLevel level) {
        var scan = new FluidSectionScan();
        BlockPos pos = new BlockPos(0, 144, 0);
        var chunk = level.getChunk(0, 0);
        if (scan.contains(chunk, 9, false) || scan.contains(chunk, 9, true)) throw new AssertionError("Dry fixture contains fluid");
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        scan.reset();
        if (!scan.contains(chunk, 9, false)) throw new AssertionError("Water addition missed by next batch");
        level.setBlock(pos.above(), Blocks.LAVA.defaultBlockState(), 3);
        scan.reset();
        if (!scan.contains(chunk, 9, true)) throw new AssertionError("Lava addition missed by next batch");
        level.setBlock(pos.above(2), Blocks.OAK_SLAB.defaultBlockState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true), 3);
        scan.reset();
        if (!scan.contains(chunk, 9, false)) throw new AssertionError("Waterlogged state excluded");
        if (scan.contains(chunk, level.getMaxSectionY(), true)) throw new AssertionError("Out-of-world scan should be empty");
    }

    private static void checkWindUniform() throws ReflectiveOperationException {
        var type = naturality.client.systems.weather.wind.WindRendering.class;
        var prepare = type.getDeclaredMethod("prepareFrame");
        prepare.setAccessible(true);
        prepare.invoke(null);
        Object first = field(type, "uniform").get(null);
        naturality.client.systems.weather.wind.WindRendering.beginFrame();
        prepare.invoke(null);
        if (field(type, "uniform").get(null) != first)
            throw new AssertionError("Unchanged wind data allocated another GPU buffer");
        naturality.client.systems.weather.wind.WindRendering.close();
        prepare.invoke(null);
        if (field(type, "uniform").get(null) == first)
            throw new AssertionError("Closed wind buffer was reused after reload");
    }

    private static void checkClouds(ClientGameTestContext context) throws ReflectiveOperationException {
        var current = context.computeOnClient(_ -> new CloudLayerRenderer());
        var original = context.computeOnClient(_ -> new ReferenceCloudLayerRenderer());
        try {
            context.runOnClient(_ -> {
                long[] cells = new long[64];
                for (int i = 0; i < cells.length; i++) if (i % 3 != 0) cells[i] = 15;
                field(current.getClass(), "texture").set(current, new CloudLayerRenderer.TextureData(cells, 8, 8));
                field(original.getClass(), "texture").set(original, new ReferenceCloudLayerRenderer.TextureData(cells, 8, 8));
            });
            for (boolean fade : new boolean[] {false,true}) for (int style : new int[] {0,1,2})
                for (double height : new double[] {90,104,108,112,116,124}) {
                    context.runOnClient(_ -> {
                        var settings = new NaturalityConfig.CloudLayer();
                        settings.fadingSides=fade; settings.style=style;
                        var camera = new Vec3(-2.5,height,-3.5);
                        current.prepare(-1, CloudStatus.FANCY,100,64,camera,0,0,settings);
                        original.prepare(-1, CloudStatus.FANCY,100,64,camera,0,0,settings);
                        int before=field(original.getClass(),"quadCount").getInt(original), after=field(current.getClass(),"quadCount").getInt(current);
                        if (after <= 0 || after > before || (!fade || style==2) && after!=before)
                            throw new AssertionError("Cloud faces changed outside gradient culling: " + before + " / " + after);
                        if (fade && style==0 && height==90) System.out.println("CLOUD_FACES: " + before + " -> " + after + " (same 64-chunk radius)");
                        current.endFrame(); original.endFrame();
                    });
                    context.waitTick(); // Submit the GPU ring-buffer fences before another prepare.
                }
        } finally { context.runOnClient(_ -> { current.close(); original.close(); }); }
    }
    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field f=type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void probe(String name,int count,Runnable action) {
        for(int i=0;i<count;i++) action.run();
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long id=Thread.currentThread().threadId(), before=bean.getThreadAllocatedBytes(id), start=System.nanoTime();
        for(int i=0;i<count;i++) action.run();
        long elapsed=System.nanoTime()-start, bytes=bean.getThreadAllocatedBytes(id)-before;
        System.out.println("RENDER_PROBE "+name+" ns/call="+elapsed/count+" bytes/call="+bytes/count);
    }
}
