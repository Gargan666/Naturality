package naturality.test;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Random;
import naturality.client.fluid.WaterVisuals;
import naturality.client.lighting.FractionalLightField;
import naturality.client.lighting.LightSourcePlacement;
import naturality.config.NaturalityConfig;
import naturality.test.reference.ReferenceWaterVisuals;
import naturality.test.reference.ReferenceLightField;
import naturality.test.reference.ReferenceLightSourcePlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Frozen original algorithms are the oracle: optimization must preserve every output. */
public final class WaterLightPerformanceGameTest implements FabricClientGameTest {
    private static final Method CURRENT_TICK = tickMethod(WaterVisuals.class);
    private static final Method ORIGINAL_TICK = tickMethod(ReferenceWaterVisuals.class);
    @SuppressWarnings("unused") // Volatile benchmark sink prevents dead-code elimination; deliberately never read.
    private static volatile Object sink;

    @Override public void runTest(ClientGameTestContext context) {
        var original = NaturalityConfig.get().liquids;
        NaturalityConfig.get().liquids = new NaturalityConfig.Liquids();
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 112 0 0 60");
            server.runCommand("time set 6000");
            server.runOnServer(s -> {
                s.setWeatherParameters(12000, 0, false, false);
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) s.overworld().getChunk(x, z);
            });
            server.runCommand("fill -24 90 -24 24 90 24 sandstone");
            server.runCommand("fill -24 91 -24 24 102 24 water");
            server.runCommand("fill -24 96 -24 -8 100 24 smooth_sandstone");
            server.runCommand("fillbiome -24 88 -24 -1 108 24 swamp");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            context.runOnClient(client -> {
                compareWater(client, "initial/swamp boundary");
                if (!(WaterVisuals.turbidityAt(-16, 0) > 0.2F)) throw new AssertionError("Swamp fixture must be murky");
            });
            context.runOnClient(client -> {
                benchmark("water-original", 96, () -> tick(ORIGINAL_TICK, client));
                benchmark("water-cached", 96, () -> tick(CURRENT_TICK, client));
            });
            server.runCommand("fill 1 103 1 8 103 8 stone");
            server.runCommand("fill -7 91 -7 -1 102 -1 air");
            server.runCommand("fill 25 91 -8 30 102 8 water");
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> compareWater(client, "roof/removal/new water"));
            server.runCommand("fillbiome -24 88 -24 -1 108 24 plains");
            server.runCommand("fillbiome 0 88 -24 24 108 24 plains");
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> {
                compareWater(client, "biome packet");
                if (WaterVisuals.turbidityAt(-16, 0) != 0) throw new AssertionError("Biome packet must remove swamp turbidity");
            });
            for (int x : new int[] {33, -33, 1}) {
                server.runCommand("tp @a " + x + " 112 0 0 60");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(2);
                context.runOnClient(client -> compareWater(client, "window shift " + x));
            }
            context.runOnClient(client -> {
                WaterVisuals.close();
                ReferenceWaterVisuals.close();
                compareWater(client, "resource rebuild");
            });
            context.runOnClient(client -> {
                TestFixtures.level(client).getChunkSource().drop(new net.minecraft.world.level.ChunkPos(0, 0));
                compareWater(client, "chunk unload");
                if (!Float.isNaN(WaterVisuals.surface(10, 10))) throw new AssertionError("Unloaded water stayed mapped");
            });
            for (int replacement = 0; replacement < 2; replacement++) {
                server.runOnServer(s -> {
                    var level = s.overworld();
                    var packet = new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                        level.getChunk(0, 0), level.getLightEngine(), null, null);
                    for (var player : s.getPlayerList().getPlayers()) player.connection.send(packet);
                });
                world.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> {
                    compareWater(client, "chunk load/replacement packet");
                    if (!Float.isFinite(WaterVisuals.surface(10, 10))) throw new AssertionError("Reloaded water disappeared");
                });
                if (replacement == 0) context.runOnClient(client -> {
                    TestFixtures.level(client).setBlock(new BlockPos(10, 103, 10), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    compareWater(client, "temporary local roof");
                    if (!Float.isNaN(WaterVisuals.surface(10, 10))) throw new AssertionError("Local roof did not obscure water");
                });
            }
            server.runCommand("fill -8 119 -8 8 119 8 stone");
            server.runCommand("fill 2 120 -5 2 124 5 stone");
            server.runCommand("fill -4 120 -4 -4 120 4 oak_slab[type=bottom]");
            server.runCommand("fill -6 120 -4 -6 120 4 oak_leaves[persistent=true]");
            server.runCommand("fill -2 120 -4 -2 120 4 snow[layers=5]");
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> checkLighting(client.level));
        } finally {
            context.runOnClient(_ -> ReferenceWaterVisuals.close());
            NaturalityConfig.get().liquids = original;
        }
    }

    private static void compareWater(Minecraft client, String stage) {
        // Both implementations keep the original 32-tick rotating refresh cadence.
        for (int i = 0; i < 33; i++) { tick(CURRENT_TICK, client); tick(ORIGINAL_TICK, client); }
        NativeImage current = image(WaterVisuals.class), original = image(ReferenceWaterVisuals.class);
        for (int z = 0; z < 256; z++) for (int x = 0; x < 256; x++)
            if (current.getPixel(x, z) != original.getPixel(x, z))
                throw new AssertionError("Water pixel differs at " + x + "," + z + " during " + stage
                    + ": " + Integer.toHexString(current.getPixel(x, z)) + " / " + Integer.toHexString(original.getPixel(x, z)));
        System.out.println("Water equivalence: " + stage + " (65536 identical pixels)");
    }

    private static void checkLighting(ClientLevel level) {
        Random random = new Random(832161);
        for (int i = 0; i < 180; i++) {
            Vec3 owner = new Vec3(random.nextDouble() * 16 - 8, 119 + random.nextDouble() * 6, random.nextDouble() * 12 - 6);
            Vec3 desired = owner.add(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5);
            checkPlacement(level, desired, owner);
        }
        for (double delta : new double[] {-1.0 / 128, 0, 1.0 / 128, 1.0 / 64})
            for (int x : new int[] {-6, -4, -2, 2, 3})
                for (double y : new double[] {120, 120.5, 120.625, 121}) {
                    Vec3 point = new Vec3(x + delta, y + delta, .5);
                    checkPlacement(level, point, point);
                }
        for (boolean precise : new boolean[] {false, true}) for (int strength : new int[] {1, 2, 7, 14, 15}) {
            var current = new FractionalLightField(level);
            var original = new ReferenceLightField(level);
            for (Vec3 point : new Vec3[] {new Vec3(.31, 121.75, -.63), new Vec3(-5.5, 122.1, 3.6), new Vec3(3.8, 122, .12)}) {
                if (precise) { current.add(point, strength); original.add(point, strength); }
                else { current.addBlock(BlockPos.containing(point), strength); original.addBlock(BlockPos.containing(point), strength); }
            }
            if (!current.seeds().equals(original.seeds()) || !current.propagate().equals(original.propagate()))
                throw new AssertionError("Light field changed, precise=" + precise + ", strength=" + strength);
        }
        Vec3 point = new Vec3(.31, 122.75, -.63);
        benchmark("placement-original", 1000, () -> sink = ReferenceLightSourcePlacement.resolve(level, point, point));
        benchmark("placement-optimized", 1000, () -> sink = LightSourcePlacement.resolve(level, point, point));
        benchmark("field-original", 128, () -> { var field = new ReferenceLightField(level); field.add(point, 14); sink = field.propagate(); });
        benchmark("field-optimized", 128, () -> { var field = new FractionalLightField(level); field.add(point, 14); sink = field.propagate(); });
        System.out.println("Lighting equivalence: collision/clearance placements and complete precise/block light fields passed");
    }

    private static void checkPlacement(ClientLevel level, Vec3 desired, Vec3 owner) {
        var current = LightSourcePlacement.resolve(level, desired, owner);
        var original = ReferenceLightSourcePlacement.resolve(level, desired, owner);
        if (!Objects.equals(current, original)) throw new AssertionError("Emitter placement changed: " + current + " / " + original);
    }

    private static void benchmark(String name, int count, Runnable action) {
        for (int i = 0; i < count; i++) action.run();
        var bean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().threadId(), allocated = bean.getThreadAllocatedBytes(id), start = System.nanoTime();
        for (int i = 0; i < count; i++) action.run();
        long nanos = System.nanoTime() - start, bytes = bean.getThreadAllocatedBytes(id) - allocated;
        System.out.printf(java.util.Locale.ROOT, "Water/light benchmark %s: %.3f us/call, %.0f bytes/call (%d calls)%n",
            name, nanos / 1000.0 / count, (double)bytes / count, count);
    }

    private static Method tickMethod(Class<?> type) {
        try { var method = type.getDeclaredMethod("tick", Minecraft.class); method.setAccessible(true); return method; }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void tick(Method method, Minecraft client) {
        try { method.invoke(null, client); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static NativeImage image(Class<?> type) {
        try { var field = type.getDeclaredField("image"); field.setAccessible(true); return (NativeImage)field.get(null); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
