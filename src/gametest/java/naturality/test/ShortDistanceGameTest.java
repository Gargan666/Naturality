package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Sea-level sky, shoreline and clouds at the minimum render distance. */
public final class ShortDistanceGameTest implements FabricClientGameTest {
    public static volatile boolean probeShore;
    public static final java.util.concurrent.atomic.AtomicInteger shoreFaces = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger badShoreVertices = new java.util.concurrent.atomic.AtomicInteger();
    @Override public void runTest(ClientGameTestContext context) {
        shoreFaces.set(0); badShoreVertices.set(0); probeShore = true;
        int[] distance = new int[1];
        boolean[] oit = new boolean[1];
        var cloud = new net.minecraft.client.CloudStatus[1];
        context.runOnClient(c -> {
            distance[0] = c.options.renderDistance().get();
            oit[0] = c.options.improvedTransparency().get();
            cloud[0] = c.options.cloudStatus().get();
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            context.runOnClient(c -> {
                c.options.renderDistance().set(2);
                c.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
            });
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runOnServer(s -> {
                for (int x = -3; x <= 3; x++) for (int z = -2; z <= 4; z++) s.overworld().getChunk(x, z);
            });
            server.runCommand("fill -40 59 -16 40 59 48 stone");
            server.runCommand("fill -40 60 -16 40 63 48 water");
            server.runCommand("fill -18 60 -8 -4 64 40 grass_block");
            for (int z : new int[]{0, 12, 24, 36}) {
                server.runCommand("fill -8 65 " + z + " -8 70 " + z + " oak_log");
                server.runCommand("fill -10 69 " + (z - 2) + " -6 72 " + (z + 2) + " oak_leaves[persistent=true]");
            }
            // Full block sides extend 1/9 block above source water. That exposed
            // strip must receive air fog even though the rest of the face is wet.
            server.runCommand("fill 3 62 28 16 63 40 dirt");
            server.runCommand("tp @a 0 65 0 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.runOnClient(c -> {
                if (c.options.getEffectiveRenderDistance() != 2 || !naturality.client.fluid.WaterVisuals.ready())
                    throw new AssertionError("Two-chunk test requires active water optics and a 32-block draw range");
            });
            if (shoreFaces.get() == 0 || badShoreVertices.get() != 0)
                throw new AssertionError("Partial shoreline mesh tags: faces=" + shoreFaces + ", bad vertices=" + badShoreVertices);
            System.out.println("Verified partial shoreline faces: " + shoreFaces);
            for (int range : new int[]{2, 8}) {
                context.runOnClient(c -> c.options.renderDistance().set(range));
                for (boolean mode : new boolean[]{false, true}) {
                    context.runOnClient(c -> c.options.improvedTransparency().set(mode));
                    for (int pitch : new int[]{-65, -20, 12}) {
                        server.runCommand("tp @a 0 65 0 0 " + pitch);
                        world.getConnection().waitForClientboundPackets();
                        context.waitTicks(8);
                        context.takeScreenshot("short-distance-" + range + "-" + mode + "-" + pitch);
                    }
                }
            }
            // Stand in a dry depression below the nearby water level. Sky rays
            // must not be absorbed by water columns beyond the visible surface.
            server.runCommand("fill -3 59 -3 3 64 3 barrier");
            server.runCommand("fill -2 60 -2 2 66 2 air");
            context.runOnClient(c -> c.options.renderDistance().set(2));
            for (int pitch : new int[]{-65, -20, 12}) {
                server.runCommand("tp @a 0 60 0 0 " + pitch);
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(12);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("short-distance-dry-below-water-" + pitch);
            }
            // Elevated water stored in nearby loaded chunks but beyond the
            // terrain's two-chunk draw range must not project onto open sky.
            server.runCommand("fill -32 89 48 32 89 63 barrier");
            server.runCommand("fill -32 90 48 32 94 63 water");
            server.runCommand("tp @a 0 65 0 0 -20");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("short-distance-out-of-range-water");
            for (int height : new int[]{85, 100, 116}) {
                server.runCommand("tp @a 0 " + height + " 0 25 65");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(12);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("short-distance-aerial-" + height);
            }
        } finally {
            probeShore = false;
            context.runOnClient(c -> {
                c.options.renderDistance().set(distance[0]);
                c.options.improvedTransparency().set(oit[0]);
                c.options.cloudStatus().set(cloud[0]);
            });
        }
    }
}
