package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;


/** Foreground player/entities against distant water and submerged cutout plants. */
public final class WaterFogOcclusionGameTest implements FabricClientGameTest {
    public static volatile boolean probe;
    public static final java.util.concurrent.atomic.AtomicInteger floorFaces = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger plantFaces = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger dryFaces = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger badFaces = new java.util.concurrent.atomic.AtomicInteger();
    @Override public void runTest(ClientGameTestContext context) {
        floorFaces.set(0); plantFaces.set(0); dryFaces.set(0); badFaces.set(0);
        probe = true;
        int[] distance = new int[1];
        boolean[] oit = new boolean[1];
        CameraType[] camera = new CameraType[1];
        context.runOnClient(client -> {
            distance[0] = client.options.renderDistance().get();
            oit[0] = client.options.improvedTransparency().get();
            camera[0] = client.options.getCameraType();
            client.options.renderDistance().set(5);
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> client.options.renderDistance().set(4));
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set 6000");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runOnServer(s -> {
                s.getPlayerList().setViewDistance(5);
                for (int x = -2; x <= 2; x++) for (int z = -1; z <= 5; z++) s.overworld().getChunk(x, z);
            });
            server.runCommand("fill -17 92 15 17 103 15 barrier");
            server.runCommand("fill -17 92 80 17 103 80 barrier");
            server.runCommand("fill -17 92 15 -17 103 80 barrier");
            server.runCommand("fill 17 92 15 17 103 80 barrier");
            server.runCommand("fill -16 92 16 16 92 79 sandstone");
            server.runCommand("fill 12 103 56 14 109 56 smooth_quartz");
            for (int y = 93; y <= 102; y += 5)
                server.runCommand("fill -16 " + y + " 16 16 " + (y + 4) + " 79 water");
            for (int z : new int[]{40, 56, 64, 72}) for (int x : new int[]{-8, -4, 0, 4, 8}) {
                server.runCommand("fill " + x + " 93 " + z + " " + x + " 100 " + z + " kelp_plant");
                server.runCommand("setblock " + x + " 101 " + z + " kelp");
            }
            server.runCommand("fill -2 102 -2 2 102 2 barrier");
            server.runCommand("tp @a 0 103 0 0 -4");
            server.runCommand("summon villager 1.7 103 5 {NoAI:1b,NoGravity:1b,Silent:1b}");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(60);
            world.getConnection().waitForChunksRender();
            if (floorFaces.get() == 0 || plantFaces.get() == 0 || dryFaces.get() == 0 || badFaces.get() != 0)
                throw new AssertionError("Submerged mesh tags: floor=" + floorFaces + ", plants=" + plantFaces
                    + ", dry=" + dryFaces + ", bad vertices=" + badFaces);
            System.out.println("Verified emitted mesh tags: floor=" + floorFaces + ", plants=" + plantFaces + ", dry=" + dryFaces);
            context.runOnClient(client -> System.out.println("Water occlusion effective distance: " + client.options.getEffectiveRenderDistance()));
            for (boolean mode : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(mode));
                context.waitTicks(8);
                context.takeScreenshot("water-foreground-occlusion-" + mode);
            }
            server.runCommand("tp @a 0 116 2 0 14");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(12);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("water-submerged-horizon");
            server.runCommand("setblock 0 93 25 barrier");
            server.runCommand("setblock 1 94 25 sea_lantern");
            server.runCommand("tp @a 0 94 25 0 -10");
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                client.options.renderDistance().set(2);
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(12);
            world.getConnection().waitForChunksRender();
            for (boolean mode : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(mode));
                context.waitTicks(8);
                context.takeScreenshot("water-underwater-player-occlusion-" + mode);
            }
        } finally {
            probe = false;
            context.runOnClient(client -> {
                client.options.renderDistance().set(distance[0]);
                client.options.improvedTransparency().set(oit[0]);
                client.options.setCameraType(camera[0]);
            });
        }
    }
}


