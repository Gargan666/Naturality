package naturality.test;

import naturality.sky.*;
import naturality.client.sky.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class BloodMoonGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        check(Double.compare(BloodMoonCycle.START_CHANCE, .05) == 0, "Blood Moon has a 5% nightfall roll");
        var cycle = new BloodMoonCycle(17);
        cycle.tick(true, false); cycle.tick(true, true);
        check(cycle.strength() == 0, "First fixture roll is unsuccessful");
        var quiet = cycle.snapshot();
        for (int i = 0; i < 10000; i++) cycle.tick(true, true);
        check(cycle.snapshot().equals(quiet), "Blood Moon never retries within a night");
        for (int i = 0; i < 2000 && cycle.strength() == 0; i++) { cycle.tick(true, false); cycle.tick(true, true); }
        check(cycle.strength() == 1, "Natural Blood Moon toggles on");
        var restored = new BloodMoonCycle(1); restored.restore(cycle.snapshot());
        for (int i = 0; i < 1000; i++) { cycle.tick(true, true); restored.tick(true, true); }
        check(cycle.snapshot().equals(restored.snapshot()) && cycle.strength() == 1, "Saved night remains active without a second roll");
        cycle.tick(true, false); check(cycle.strength() == 0, "Natural Blood Moon ends at dawn");
        var oldClouds = naturality.config.NaturalityConfig.get().clouds;
        var oldStatus = new net.minecraft.client.CloudStatus[1];
        boolean[] oldOit = new boolean[1];
        context.runOnClient(client -> {
            oldStatus[0] = client.options.cloudStatus().get(); oldOit[0] = client.options.improvedTransparency().get();
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("time set midnight");
            server.runCommand("gamemode spectator @a");
            server.runOnServer(s -> s.setWeatherParameters(24000, 0, false, false));
            server.runCommand("tp @a 0 90 0 0 -90");
            server.runOnServer(s -> {
                var old = SkyEventWorldData.get(s).settings(s.overworld(), SkyEventType.BLOOD_MOON);
                old.override = true; old.strength = 20;
                check(SkyEvents.strength(s.overworld(), SkyEventType.BLOOD_MOON) == 1 && old.strength == 1,
                    "Old saved strengths become the full on state");
            });
            server.runCommand("skyevent minecraft:overworld blood_moon 0"); context.waitTicks(20);
            server.runOnServer(s -> {
                boolean rejected = false;
                try { s.getCommands().getDispatcher().execute("skyevent minecraft:overworld blood_moon 2", s.createCommandSourceStack()); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException expectedError) { rejected = true; }
                check(rejected && SkyEvents.strength(s.overworld(), SkyEventType.BLOOD_MOON) == 0,
                    "The toggle rejects numeric strengths above one");
            });
            context.runOnClient(_ -> check(BloodMoonClient.current() == 0, "Normal moon starts without a blood tint"));
            context.takeScreenshot("blood-moon-normal");
            server.runCommand("skyevent minecraft:overworld blood_moon on"); context.waitTicks(25);
            context.runOnClient(_ -> check(BloodMoonClient.current() > .1F && BloodMoonClient.current() < .9F,
                "Command tint and scale ease toward their target"));
            context.takeScreenshot("blood-moon-transition");
            context.waitTicks(150);
            context.runOnClient(client -> {
                check(BloodMoonClient.current() == 1 && Math.abs(BloodMoonClient.scale(1) - 1.1F) < .00001,
                    "Full Blood Moon is red and ten percent larger");
                var color = BloodMoonClient.tint(new org.joml.Vector3f(.6F, .7F, 1), 1);
                check(color.x == 1 && color.y == 0 && color.z == 0, "Shared tint is fully red at maximum strength");
                check(BloodMoonClient.tintArgb(0x806080ff, 1) == 0x80ff0000,
                    "General cloud tint becomes red while preserving cloud alpha and peak brightness");
                var extractor = new net.minecraft.client.renderer.LightmapRenderStateExtractor(client.gameRenderer, client);
                var light = new net.minecraft.client.renderer.state.LightmapRenderState();
                extractor.tick(); extractor.extract(light, 1);
                var originalLight = client.gameRenderer.mainCamera().attributeProbe().getValue(
                    net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_COLOR, 1);
                var expectedLight = new org.joml.Vector3f(originalLight).lerp(BloodMoonClient.tint(originalLight, 1), .5F);
                check(new org.joml.Vector3f(light.skyLightColor).distance(expectedLight) < .00001,
                    "Actual world lightmap receives half the eased Blood Moon tint");
            });
            context.takeScreenshot("blood-moon-full");
            context.runOnClient(client -> {
                naturality.config.NaturalityConfig.get().clouds = new naturality.config.NaturalityConfig.Clouds();
                client.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
            });
            server.runCommand("time set 15000");
            server.runCommand("tp @a 0.5 164 0.5 -90 -30");
            for (boolean oit : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(oit));
                context.waitTicks(20);
                context.takeScreenshot("blood-moon-cloud-rim-" + oit);
            }
            server.runCommand("skyevent minecraft:overworld status");
            server.runCommand("skyevent minecraft:overworld blood_moon off"); context.waitTicks(25);
            context.runOnClient(_ -> check(BloodMoonClient.current() > .1F && BloodMoonClient.current() < .9F,
                "Switching off reverses moon, cloud and light tint gradually"));
            context.waitTicks(150);
            context.runOnClient(_ -> check(BloodMoonClient.current() == 0 && BloodMoonClient.scale(1) == 1,
                "The original moon and skylight are restored"));
            context.runOnClient(_ -> check(BloodMoonClient.tintArgb(0x806080ff, 1) == 0x806080ff,
                "Original blue cloud colors return when Blood Moon fades out"));
            server.runCommand("skyevent minecraft:overworld blood_moon 1"); context.waitTicks(160);
            server.runCommand("time set noon"); context.waitTicks(160);
            context.runOnClient(_ -> check(BloodMoonClient.current() == 0, "Even an override fades out during daylight"));
            server.runCommand("skyevent minecraft:overworld blood_moon auto");
        } finally {
            naturality.config.NaturalityConfig.get().clouds = oldClouds;
            context.runOnClient(client -> {
                client.options.cloudStatus().set(oldStatus[0]); client.options.improvedTransparency().set(oldOit[0]);
            });
        }
        try (var _ = context.worldBuilder().create()) {
            context.waitTicks(5);
            context.runOnClient(_ -> check(BloodMoonClient.current() == 0, "Another world inherits no blood tint"));
        }
    }
}
