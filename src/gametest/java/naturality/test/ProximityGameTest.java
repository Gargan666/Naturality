package naturality.test;

import naturality.sky.*;
import naturality.client.sky.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class ProximityGameTest implements FabricClientGameTest {
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        var cycle = new ProximityCycle(17);
        for (int i = 0; i < 100000; i++) cycle.tick(true, false);
        check(cycle.strength() == 0, "Proximity cannot start during day");
        check(Double.compare(ProximityCycle.START_CHANCE, .05) == 0, "Proximity has a 5% chance at nightfall");
        cycle.tick(true, true);
        var firstNight = cycle.snapshot();
        check(cycle.strength() == 0, "Fixture's first nightfall roll is unsuccessful");
        for (int i = 0; i < 10000; i++) cycle.tick(true, true);
        check(cycle.snapshot().equals(firstNight), "A failed nightfall roll never retries during that night");
        for (int i = 0; i < 2000 && cycle.strength() == 0; i++) {
            cycle.tick(true, false); cycle.tick(true, true);
        }
        int selected = cycle.strength(); long appearance = cycle.appearance();
        check(selected >= 1 && selected <= 7 && cycle.remaining() >= 48000,
            "A random valid planet lasts at least two days");
        var restored = new ProximityCycle(1); restored.restore(cycle.snapshot());
        var ending = new ProximityCycle(1);
        var nearEnd = new java.util.HashMap<>(cycle.snapshot()); nearEnd.put("remaining", 1L);
        ending.restore(nearEnd); ending.tick(true, true);
        var finished = ending.snapshot();
        for (int i = 0; i < 10000; i++) ending.tick(true, true);
        check(ending.snapshot().equals(finished), "An event ending during night waits until another nightfall to roll");
        for (int i = 0; i < 24000; i++) {
            cycle.tick(true, i % 24000 > 13000); restored.tick(true, i % 24000 > 13000);
            check(cycle.strength() == selected && cycle.appearance() == appearance,
                "Planet and position persist across daylight and the next night");
            check(cycle.snapshot().equals(restored.snapshot()), "Saved Proximity resumes the same episode and random sequence");
        }
        for (int seed = 0; seed < 1000; seed++) for (int time = 0; time <= 120000; time += 10000) {
            var direction = ProximityRenderer.direction(seed, time);
            check(direction.y > .2F && Math.abs(direction.z) > .4F,
                "Small X-axis drift preserves horizon clearance");
            check(direction.x == ProximityRenderer.direction(seed, 0).x,
                "Independent motion rotates about X without changing the X component");
        }
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        var expected = cycle.snapshot();
        try (var world = TestWorlds.persistent(context).create()) {
            save = world.getWorldSave();
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("time set midnight");
            server.runOnServer(s -> s.setWeatherParameters(24000, 0, false, false));
            for (int planet = 1; planet <= 7; planet++) {
                server.runCommand("skyevent minecraft:overworld proximity " + planet);
                context.waitTicks(12);
                context.runOnClient(_ -> check(ProximityRenderer.opacity() > 0 && ProximityRenderer.opacity() < .8F,
                    "Newly selected planets fade in from zero opacity"));
                context.waitTicks(20);
                final int index = planet;
                float[] heading = new float[2];
                context.runOnClient(client -> {
                    check(SkyEventsClient.strength(client.level, SkyEventType.PROXIMITY) == index,
                        "Planet override synchronizes exactly without easing through other planets");
                    var texture = naturality.Naturality.id("textures/sky/" + ProximityCycle.PLANETS[index - 1] + ".png");
                    check(client.getResourceManager().getResource(texture).isPresent(), "Supplied planet sprite is packaged");
                    long seed = SkyEventsClient.appearance(TestFixtures.level(client), SkyEventType.PROXIMITY) ^ index * 341873128712L;
                    var direction = ProximityRenderer.direction(seed, TestFixtures.level(client).getGameTime());
                    heading[0] = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
                    heading[1] = (float)-Math.toDegrees(Math.asin(direction.y));
                });
                server.runCommand("tp @a 0 220 0 " + heading[0] + " " + heading[1]);
                context.waitTicks(3);
                context.runOnClient(_ -> check(ProximityRenderer.drawn() == 1, "One planet sprite draws at night"));
                context.takeScreenshot("proximity-" + ProximityCycle.PLANETS[planet - 1]);
            }
            server.runCommand("skyevent minecraft:overworld status");
            float[] nightOpacity = new float[1];
            context.runOnClient(_ -> nightOpacity[0] = ProximityRenderer.opacity());
            server.runCommand("time set noon"); context.waitTicks(5);
            context.runOnClient(_ -> check(ProximityRenderer.opacity() > 0 && ProximityRenderer.opacity() < nightOpacity[0],
                "Planet fades gradually when daytime arrives instead of disappearing"));
            context.waitTicks(45);
            context.runOnClient(_ -> check(ProximityRenderer.drawn() == 0,
                "Daylight hides the planet without changing its selection"));
            server.runCommand("time set midnight"); context.waitTicks(15);
            context.runOnClient(_ -> check(ProximityRenderer.drawn() == 1,
                "The same planet returns on the next night"));
            server.runCommand("skyevent minecraft:overworld proximity 0"); context.waitTicks(20);
            context.runOnClient(_ -> check(ProximityRenderer.drawn() == 0, "Zero disables Proximity"));
            server.runCommand("skyevent minecraft:overworld proximity auto");
            server.runOnServer(s -> check(!SkyEventWorldData.get(s).settings(s.overworld(), SkyEventType.PROXIMITY).override,
                "Automatic scheduling can be restored"));
            server.runOnServer(s -> {
                s.overworld().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER, false, s);
                SkyEvents.proximityCycle(s.overworld()).restore(expected);
                naturality.weather.EnvironmentWorldData.get(s).write("sky/minecraft:overworld/proximity", expected);
                boolean rejected = false;
                try { s.getCommands().getDispatcher().execute("skyevent minecraft:overworld proximity 8", s.createCommandSourceStack()); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException expectedError) { rejected = true; }
                check(rejected && !SkyEventWorldData.get(s).settings(s.overworld(), SkyEventType.PROXIMITY).override,
                    "Strength 8 is rejected without changing the automatic event");
            });
        }
        try (var other = TestWorlds.disposable(context).create()) {
            other.getServer().runOnServer(s -> check(SkyEvents.strength(s.overworld(), SkyEventType.PROXIMITY) == 0,
                "Another world does not inherit the active planet"));
        }
        try (var reopened = save.open()) {
            context.waitTicks(20);
            reopened.getServer().runOnServer(s -> check(SkyEvents.proximityCycle(s.overworld()).snapshot().equals(expected),
                "A real save/reopen preserves the selected planet, position and remaining episode"));
            context.runOnClient(client -> check(SkyEventsClient.strength(client.level, SkyEventType.PROXIMITY) == selected,
                "The restored planet synchronizes again after world reload"));
        }
    }
}
