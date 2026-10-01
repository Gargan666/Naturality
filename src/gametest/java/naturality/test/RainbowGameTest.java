package naturality.test;

import naturality.client.sky.RainbowRenderer;
import naturality.client.weather.ParticleWeather;
import naturality.sky.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CloudStatus;
import org.joml.Vector3f;

public final class RainbowGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        double peak = RainbowCycle.startChance(10);
        check(Math.abs(1 - Math.pow(1 - peak, 1200) - .30) < .00001, "Rain ten peaks at 30% per minute");
        check(RainbowCycle.startChance(1) == 0 && RainbowCycle.startChance(20) == 0
            && RainbowCycle.startChance(0) == 0 && RainbowCycle.startChance(21) == 0,
            "Rainbow only starts between rain one and twenty");
        check(RainbowCycle.startChance(5) < peak && RainbowCycle.startChance(15) < peak,
            "Start chance eases away from rain ten");
        check(RainbowRenderer.daylightFade(1000) == 1 && RainbowRenderer.daylightFade(11000) == 1
            && Math.abs(RainbowRenderer.daylightFade(12000) - .5F) < .0001F
            && RainbowRenderer.daylightFade(13000) == 0 && RainbowRenderer.daylightFade(18000) == 0,
            "Rainbow fades through sunset and is absent at night");
        check(RainbowRenderer.easedSize(0) == 0 && RainbowRenderer.easedSize(10) == .5F
            && RainbowRenderer.easedSize(20) == 1
            && RainbowRenderer.easedSize(5) < .25F && RainbowRenderer.easedSize(15) > .75F,
            "Rainbow radius uses exponential ease-in-out");
        check(RainbowRenderer.altitudeFade(184, 200) == 1
            && Math.abs(RainbowRenderer.altitudeFade(192, 200) - .5F) < .0001F
            && RainbowRenderer.altitudeFade(200, 200) == 0
            && RainbowRenderer.altitudeFade(208, 200) == 0
            && RainbowRenderer.altitudeFade(208, Double.POSITIVE_INFINITY) == 1,
            "Rainbow fades across the final 16 blocks beneath the rain ceiling");
        check(RainbowRenderer.skyBlend(new Vector3f(0.8F)) == 1
            && RainbowRenderer.skyBlend(new Vector3f(0.25F)) < .15F
            && RainbowRenderer.skyBlend(new Vector3f(0.1F)) < RainbowRenderer.skyBlend(new Vector3f(0.25F)),
            "Rainbow blends progressively into darker skies");
        var cycle = new RainbowCycle(73);
        for (int i = 0; i < 120000; i++) cycle.tick(true, false, 10);
        check(!cycle.active() && cycle.strength() == 0, "Rainbow never starts at night");
        for (int i = 0; i < 120000 && !cycle.active(); i++) cycle.tick(true, true, 10);
        check(cycle.active(), "Rainbow can start in eligible daytime rain");
        float prior = cycle.strength();
        for (int i = 0; i < 400; i++) {
            cycle.tick(true, true, 10);
            check(cycle.active() && cycle.strength() >= prior,
                "Natural rainbow keeps its chosen strength for the event");
            prior = cycle.strength();
        }
        cycle.tick(true, false, 10);
        check(!cycle.active() && cycle.strength() < 20, "Sunset ends automatic rainbow activity");

        var previousClouds = new CloudStatus[1];
        context.runOnClient(client -> {
            previousClouds[0] = client.options.cloudStatus().get();
            client.options.cloudStatus().set(CloudStatus.OFF);
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("weather minecraft:overworld rain 10");
            server.runCommand("time set 1000");
            server.runCommand("tp @a 0 150 0 90 -22");
            server.runCommand("skyevent minecraft:overworld rainbow 20");
            context.waitTicks(110);
            server.runOnServer(s -> check(SkyEvents.strength(s.overworld(), SkyEventType.RAINBOW) == 20,
                "Rainbow override is allowed in daytime rain ten"));
            context.runOnClient(client -> check(RainbowRenderer.strength() == 20,
                "Rainbow strength reaches the client"));
            context.takeScreenshot("rainbow-morning");
            server.runCommand("skyevent minecraft:overworld rainbow 5");
            context.waitTicks(75);
            context.takeScreenshot("rainbow-small");
            server.runCommand("skyevent minecraft:overworld rainbow 20");
            context.waitTicks(75);
            context.takeScreenshot("rainbow-restored");
            server.runCommand("weather minecraft:overworld rain 19");
            context.waitTicks(65);
            context.takeScreenshot("rainbow-dark-rain");
            server.runCommand("weather minecraft:overworld rain 10");
            context.waitTicks(65);
            context.runOnClient(client -> client.options.cloudStatus().set(CloudStatus.FANCY));
            context.waitTicks(5);
            var cloudTop = new double[1];
            context.runOnClient(client -> cloudTop[0] = ParticleWeather.highestActiveCloudTop(client));
            check(Double.isFinite(cloudTop[0]), "Active upper clouds give rain a finite ceiling");
            server.runCommand("tp @a 0 " + (int) Math.ceil(cloudTop[0] + 8) + " 0 90 -22");
            context.waitTicks(12);
            context.takeScreenshot("rainbow-above-rain");
            server.runCommand("tp @a 0 150 0 90 -22");
            context.runOnClient(client -> client.options.cloudStatus().set(CloudStatus.OFF));
            context.waitTicks(12);
            server.runCommand("time set 4500");
            context.waitTicks(12);
            context.takeScreenshot("rainbow-approaching-middle");
            server.runCommand("time set 6000");
            context.waitTicks(12);
            context.takeScreenshot("rainbow-noon");
            server.runCommand("time set 7500");
            server.runCommand("tp @a 0 150 0 -90 -22");
            context.waitTicks(12);
            context.takeScreenshot("rainbow-leaving-middle");
            server.runCommand("time set 11000");
            context.waitTicks(15);
            context.takeScreenshot("rainbow-evening");
            server.runCommand("time set 12000");
            context.waitTicks(12);
            context.takeScreenshot("rainbow-sunset");
            server.runCommand("time set 18000");
            context.waitTicks(90);
            server.runOnServer(s -> check(SkyEvents.strength(s.overworld(), SkyEventType.RAINBOW) == 0,
                "Rainbow is unavailable at night even with an override"));
            context.runOnClient(client -> check(RainbowRenderer.strength() == 0,
                "Nighttime rainbow fades away"));
            context.takeScreenshot("rainbow-night");
            server.runCommand("time set 1000");
            server.runCommand("weather minecraft:overworld rain 20");
            context.waitTicks(60);
            server.runOnServer(s -> check(SkyEvents.strength(s.overworld(), SkyEventType.RAINBOW) == 0,
                "Rain twenty suppresses even a rainbow override"));
            server.runCommand("skyevent minecraft:overworld rainbow auto");
        } finally {
            context.runOnClient(client -> client.options.cloudStatus().set(previousClouds[0]));
        }
    }
}
