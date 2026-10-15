package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import naturality.client.rendering.heatwaves.HeatWaves;
import net.minecraft.world.phys.AABB;
import java.util.List;

public final class HeatWavesGameTest implements FabricClientGameTest {
    @SuppressWarnings("unchecked")
    private static List<AABB> volumes() {
        try {
            var field = HeatWaves.class.getDeclaredField("boxes"); field.setAccessible(true);
            return List.copyOf((List<AABB>)field.get(null));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean test, String message) { if (!test) throw new AssertionError(message); }
    private static float proximity(AABB volume, net.minecraft.world.phys.Vec3 camera) {
        try {
            var method = HeatWaves.class.getDeclaredMethod("proximityStrength", AABB.class, net.minecraft.world.phys.Vec3.class);
            method.setAccessible(true);
            return (float)method.invoke(null, volume, camera);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static float uploadedProximity() {
        try {
            var field = HeatWaves.class.getDeclaredField("data"); field.setAccessible(true);
            var data = (java.nio.ByteBuffer)field.get(null);
            float largest = 0;
            for (int i = 0; i < (int)data.getFloat(0); i++) largest = Math.max(largest, data.getFloat(28 + i * 32));
            return largest;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Override public void runTest(ClientGameTestContext context) {
        var volume = new AABB(-1, -1, -1, 1, 1, 1);
        check(proximity(volume, new net.minecraft.world.phys.Vec3(21, 0, 0)) == 0, "Range boundary starts at zero");
        check(proximity(volume, new net.minecraft.world.phys.Vec3(9, 0, 0)) == 1, "Nearby volumes reach full strength");
        check(proximity(volume, net.minecraft.world.phys.Vec3.ZERO) == 1, "Inside heat volume remains finite and full strength");
        float previous = 0;
        for (double distance = 20; distance >= 8; distance -= 0.125) {
            float next = proximity(volume, new net.minecraft.world.phys.Vec3(-1-distance, 0, 0));
            check(next >= previous && next <= 1 && next - previous < .02, "Approach fade must be continuous and monotonic");
            previous = next;
        }
        check(proximity(volume, new net.minecraft.world.phys.Vec3(20.99, 0, 0)) < .00001,
            "First visible proximity must not snap to appreciable strength");
        boolean[] saved = {false};
        int[] savedPower = {0};
        context.runOnClient(c -> {
            saved[0] = c.options.improvedTransparency().get();
            savedPower[0] = naturality.config.live.NaturalityConfig.get().liquids.heatWavePower;
            naturality.config.live.NaturalityConfig.get().liquids.heatWavePower = 50;
        });
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            server.runCommand("tp @a 0 103 -10 0 10");
            server.runCommand("fill -12 99 -4 12 99 8 stone");
            server.runCommand("fill -12 100 7 12 108 7 quartz_block");
            for (int x=-12; x<=12; x+=2) server.runCommand("fill "+x+" 100 7 "+x+" 108 7 black_concrete");
            server.runCommand("setblock -3 100 0 lava");
            server.runCommand("setblock 3 99 0 netherrack");
            server.runCommand("setblock 3 100 0 fire");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            context.runOnClient(c -> {
                var boxes = volumes();
                check(boxes.stream().anyMatch(b -> b.minX == -5 && b.minY == 98 && b.maxY == 103), "Two-block lava halo");
                check(boxes.stream().anyMatch(b -> b.minX == 3 && b.minY == 101 && b.maxY == 103 && b.maxX == 4), "Fire heat is two blocks above only");
                HeatWaveOptimizationChecks.verify(c, "lava-fire", true);
            });
            float[] approach = new float[2];
            int index = 0;
            for (int z : new int[]{-19, -13}) {
                int sample = index++;
                server.runCommand("tp @a 0 103 " + z + " 0 8");
                world.getConnection().waitForClientboundPackets(); context.waitTicks(6);
                context.runOnClient(_ -> approach[sample] = uploadedProximity());
                context.takeScreenshot("heat-waves-proximity-" + sample);
            }
            check(approach[0] > 0 && approach[0] < .5 && approach[1] > approach[0] && approach[1] <= 1,
                "Actual camera approach uploads gradually increasing strength");
            server.runCommand("tp @a 0 103 -10 0 10");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(6);
            for (boolean oit : new boolean[]{false,true}) {
                context.runOnClient(c -> c.options.improvedTransparency().set(oit));
                context.waitTicks(8); context.takeScreenshot("heat-waves-"+oit);
            }
            long[] scanAtZero = new long[1];
            context.runOnClient(_ -> {
                naturality.config.live.NaturalityConfig.get().liquids.heatWavePower = 0;
                scanAtZero[0] = (long)HeatWaveOptimizationChecks.field(HeatWaves.class, "scanned");
            });
            context.waitTicks(7);
            context.runOnClient(_ -> check(scanAtZero[0] == (long)HeatWaveOptimizationChecks.field(HeatWaves.class, "scanned"),
                "Zero power must not scan sources"));
            for (int power : new int[]{0, 200}) {
                context.runOnClient(_ -> naturality.config.live.NaturalityConfig.get().liquids.heatWavePower = power);
                context.waitTicks(2); context.takeScreenshot("heat-waves-power-"+power);
            }
            server.runCommand("tp @a 0 104 -10 0 17");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
            context.takeScreenshot("heat-waves-world-bands-moved-camera");
            server.runCommand("fill -8 100 -5 8 103 5 air");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(10);
            context.runOnClient(_ -> check(volumes().isEmpty(), "Removed heat sources clear the effect"));
            // A single merged lake must still contain many independently moving patches.
            server.runCommand("fill -12 100 -4 12 100 6 lava");
            server.runCommand("tp @a 0 106 -10 0 25");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(12);
            context.takeScreenshot("heat-waves-large-lake-local-patches");
            context.waitTicks(7);
            context.takeScreenshot("heat-waves-large-lake-local-patches-later");
            context.runOnClient(c -> {
                HeatWaveOptimizationChecks.verify(c, "lake", true);
                HeatWaveOptimizationChecks.synthetic(c);
                HeatWaves.close();
            });
            context.waitTicks(2);
            context.runOnClient(_ -> check(HeatWaveOptimizationChecks.field(HeatWaves.class, "uniforms") != null,
                "Renderer recreates resources after close"));
        } finally { context.runOnClient(c -> {
            naturality.test.reference.ReferenceHeatWaves.close();
            c.options.improvedTransparency().set(saved[0]);
            naturality.config.live.NaturalityConfig.get().liquids.heatWavePower = savedPower[0];
        }); }
    }
}
