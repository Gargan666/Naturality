package naturality.test;

import com.mojang.blaze3d.platform.NativeImage;
import naturality.client.shadow.BlockGridShadow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;

public final class EntityShadowGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try (var stream = client.getResourceManager().open(Identifier.withDefaultNamespace("textures/misc/shadow.png"));
                 var source = NativeImage.read(stream)) {
                for (float radius : new float[]{0.1F, 0.3F, 0.5F, 0.7F, 1, 2, 32}) {
                    int pixels = BlockGridShadow.pixelRadius(radius);
                    try (var image = BlockGridShadow.generate(source, pixels)) {
                        check(image.getWidth() == pixels * 2 + 4, "One texel per 1/16 world block including padding");
                        check((image.getPixel(0, 0) >>> 24) == 0, "Transparent outer border");
                        int mid = pixels + 2;
                        check((image.getPixel(mid, mid) >>> 24) > 0, "Vanilla central shadow retained");
                        check((image.getPixel(mid, mid) & 0xFFFFFF) == (source.getPixel(source.getWidth()/2, source.getHeight()/2) & 0xFFFFFF), "Vanilla shadow tint retained");
                    }
                }
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            for (double entityX : new double[]{-30000000.123, -1.03, -0.001, 0, 0.031, 0.99, 1.03, 30000000.123}) {
                float relative = (float)(Math.floor(entityX) - entityX);
                double phase = BlockGridShadow.gridPhase(relative) / (double)BlockGridShadow.PHASE_SCALE;
                double worldSample = Math.floor(entityX) + 0.123;
                double pixel = (worldSample - entityX) * 16 + 10;
                double cell = Math.floor(pixel - phase) + 0.5 + phase;
                double worldCenter = entityX + (cell - 10) / 16;
                check(Math.abs(worldCenter - (Math.floor(worldSample * 16) + 0.5) / 16) < 0.000005, "Raster cells stay on the world grid at negative and distant coordinates");
                check(Math.abs(BlockGridShadow.gridPhase(relative) - BlockGridShadow.gridPhase(relative + 1)) <= 1, "Adjacent block pieces share a raster phase");
            }
            try (var disk = new NativeImage(64, 64, true)) {
                for (int z=0; z<64; z++) for (int x=0; x<64; x++) {
                    if (Math.hypot(x-31.5,z-31.5) < 24) disk.setPixel(x,z,0xFF204060);
                }
                try (var filtered = BlockGridShadow.generate(disk, 8)) {
                    int partial = 0;
                    int extraCorners = 0;
                    for (int z=0; z<20; z++) for (int x=0; x<20; x++) {
                        int alpha = filtered.getPixel(x,z) >>> 24;
                        if (alpha > 0 && alpha < 255) partial++;
                        if (alpha > 0 && Math.hypot(x-9.5,z-9.5) > 6) extraCorners++;
                        if (alpha > 0) check((filtered.getPixel(x,z) & 0xFFFFFF)==0x204060, "Transparent border cannot contaminate shadow tint");
                    }
                    check(partial > 20 && extraCorners > 0, "Soft filter adds faint corner pixels beyond the hard circle");
                }
            }
            check(BlockGridShadow.gridPhase(-0.13F) != BlockGridShadow.gridPhase(-0.14F), "Subpixel motion changes raster coverage within a single world cell");
            check(BlockGridShadow.texture(0.5F) == BlockGridShadow.texture(0.5F), "Generated texture shared by radius");
        });
        boolean[] previous = new boolean[1];
        context.runOnClient(client -> previous[0] = client.options.improvedTransparency().get());
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 103 1 0 35");
            server.runCommand("time set 6000");
            server.runCommand("fill -6 99 2 6 99 12 grass_block");
            server.runCommand("summon pig -1.13 100 6.27 {NoAI:1b,Tags:[\"shadow_motion\"]}");
            server.runCommand("summon chicken 1.21 100 6.17 {NoAI:1b}");
            server.runCommand("summon slime 3.13 100 8.27 {NoAI:1b,Size:3}");
            server.runCommand("summon pig -3.13 100 6.27 {NoAI:1b,Age:-24000}");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(30);
            for (boolean mode : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(mode));
                context.waitTicks(5);
                context.takeScreenshot("entity-shadow-grid-" + mode);
                for (int step = 0; step < 5; step++) {
                    server.runCommand("tp @e[tag=shadow_motion] " + (-1.13 + step * 0.0125) + " 100 6.27");
                    world.getConnection().waitForClientboundPackets();
                    context.waitTicks(5);
                    context.takeScreenshot("entity-shadow-motion-" + mode + "-" + step);
                }
            }
        } finally {
            context.runOnClient(client -> client.options.improvedTransparency().set(previous[0]));
        }
    }
}

