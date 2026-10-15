package naturality.test;

import naturality.config.NaturalityConfig;
import naturality.client.sky.SunBeams;
import naturality.client.sky.SunLensFlare;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Captures Naturality's animated sun beams in a clear Overworld sunset. */
public final class SunsetGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean[] previous = new boolean[2];
        context.runOnClient(client -> {
            var grayTop = naturality.client.sky.SkyGradientRenderer.zenithTint(new org.joml.Vector3f(.5F));
            if (Math.abs(grayTop.x - grayTop.z) > .0001F || grayTop.x <= .5F || grayTop.x > .57F)
                throw new AssertionError("Neutral biome zenith must remain gray and only slightly lighter");
            var blueTop = naturality.client.sky.SkyGradientRenderer.zenithTint(new org.joml.Vector3f(.48F, .65F, 1));
            if (!(blueTop.z > blueTop.y && blueTop.y > blueTop.x && blueTop.x < .48F))
                throw new AssertionError("Blue biome sky must retain its hue with a richer zenith");
            float previousSpan = 0;
            for (int chunks : new int[] {5, 8, 16, 32}) {
                var fog = new net.minecraft.client.renderer.fog.FogData();
                fog.renderDistanceEnd = chunks * 16;
                fog.renderDistanceStart = fog.renderDistanceEnd * .9F;
                naturality.client.fog.DistanceFogSpan.apply(fog, chunks);
                float span = fog.renderDistanceEnd - fog.renderDistanceStart;
                if (span <= previousSpan || fog.renderDistanceEnd != chunks * 16 || fog.renderDistanceStart <= 0)
                    throw new AssertionError("Fog must widen with distance without moving the opaque boundary");
                if (chunks == 32 && (span < 150 || span > 160))
                    throw new AssertionError("32-chunk fog should span roughly ten chunks");
                previousSpan = span;
            }
            var cloud = new NaturalityConfig.CloudLayer();
            cloud.speedPercent = 0;
            var eye = new net.minecraft.world.phys.Vec3(6, 90, 2);
            var end = eye.add(0, 200, 0);
            var occupied = new net.minecraft.client.renderer.CloudRenderer.TextureData(new long[]{1}, 1, 1);
            naturality.client.cloud.CloudSunOcclusion.beginFrame();
            naturality.client.cloud.CloudSunOcclusion.add(occupied, cloud, 100, 32, 0, 0);
            if (SunLensFlare.visibilityStrength(naturality.client.cloud.CloudSunOcclusion.transmission(eye, end)) != 0)
                throw new AssertionError("Opaque cloud must suppress flare");
            naturality.client.cloud.CloudSunOcclusion.beginFrame();
            cloud.opacityPercent = 10;
            naturality.client.cloud.CloudSunOcclusion.add(occupied, cloud, 100, 32, 0, 0);
            if (SunLensFlare.visibilityStrength(naturality.client.cloud.CloudSunOcclusion.transmission(eye, end)) != 1)
                throw new AssertionError("Faint cloud must preserve flare");
            naturality.client.cloud.CloudSunOcclusion.beginFrame();
            if (naturality.client.cloud.CloudSunOcclusion.transmission(eye, end) != 1)
                throw new AssertionError("Cloud data must not survive frame reset");
            if (SunLensFlare.visibilityStrength(0.28F) != 0
                    || SunLensFlare.visibilityStrength(0.5F) <= 0
                    || SunLensFlare.visibilityStrength(1) != 1)
                throw new AssertionError("Sun visibility must reject slivers, allow partial coverage and retain clear-sun strength");
            if (SunLensFlare.strength(SunBeams.closeness(1)) != 0.14F
                    || SunLensFlare.strength(SunBeams.closeness(0)) <= 0)
                throw new AssertionError("Flare must reach maximum strength at center and retain strength beyond the old 45-degree cone");
            float offCenter = SunLensFlare.strength(SunBeams.closeness((float) Math.cos(Math.toRadians(20))));
            float nearCenter = SunLensFlare.strength(SunBeams.closeness((float) Math.cos(Math.toRadians(5))));
            if (!(offCenter > 0 && nearCenter > offCenter))
                throw new AssertionError("Flare must brighten toward the centered sun");
            previous[0] = NaturalityConfig.get().effects.sunBeams;
            previous[1] = client.gui.hud.isHidden();
            NaturalityConfig.get().effects.sunBeams = false;
            if (!previous[1]) client.gui.hud.toggle();
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("time set 13000");
            // Yaw 90 faces west in Minecraft, toward the setting sun.
            server.runCommand("tp @a 0.5 150 0.5 90 0");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("sunset-sun-base");
            context.runOnClient(client -> NaturalityConfig.get().effects.sunBeams = true);
            context.waitTicks(10);
            context.takeScreenshot("sunset-sun-beams");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0.5 250 0.5 0 -65");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("sun-flare-offset");
            server.runCommand("tp @a 0.5 250 0.5 0 -85");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("sun-flare-near-center");
            server.runCommand("tp @a 0.5 250 0.5 0 -90");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("sun-flare-centered");
            server.runCommand("tp @a 0.5 250 0.5 35 -65");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            var reload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
            context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
            context.waitFor(client -> reload.get() != null && reload.get().isDone());
            reload.get().join();
            context.waitFor(client -> client.gui.overlay() == null);
            context.waitTicks(10);
            context.takeScreenshot("sun-flare-reloaded");
            server.runCommand("fill -3 258 -3 3 258 3 stone");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("sun-flare-blocked");
            server.runCommand("fill -3 258 -3 3 258 3 air");
            server.runCommand("summon minecraft:giant 0.5 253 0.5 {NoAI:1b,NoGravity:1b,Silent:1b,Tags:[\"flare_occluder\"]}");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("sun-flare-entity-blocked");
            server.runCommand("kill @e[tag=flare_occluder]");
            context.waitTicks(25);
            server.runCommand("time set 12000");
            server.runCommand("tp @a 0.5 250 0.5 65 -15");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("sun-flare-warm-sunset");
            server.runCommand("time set 0");
            server.runCommand("tp @a 0.5 250 0.5 -65 -15");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("sun-flare-warm-sunrise");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0.5 250 0.5 180 -25");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(15);
            context.takeScreenshot("sky-gradient-clear");
            server.runCommand("fillbiome -14 236 -14 14 264 14 minecraft:pale_garden");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            context.takeScreenshot("sky-gradient-pale-garden");
            server.runCommand("fillbiome -14 236 -14 14 264 14 minecraft:plains");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(40);
            server.runCommand("weather minecraft:overworld rain 100");
            context.waitTicks(35);
            context.takeScreenshot("sky-gradient-rain-onset");
            context.waitTicks(80);
            context.takeScreenshot("sky-gradient-rain");

        } finally {
            context.runOnClient(client -> {
                NaturalityConfig.get().effects.sunBeams = previous[0];
                NaturalityConfig.get().save();
                if (client.gui.hud.isHidden() != previous[1]) client.gui.hud.toggle();
            });
        }
    }
}
