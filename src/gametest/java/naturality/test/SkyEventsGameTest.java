package naturality.test;

import naturality.sky.*;
import naturality.client.sky.MeteorShower;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.Level;

public final class SkyEventsGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        var cycle = new SkyEventCycle(17);
        check(!cycle.active() && cycle.strength() == 0, "Starts quiet");
        check(Math.abs(1 - Math.pow(1 - SkyEventCycle.START_CHANCE_PER_TICK, 1200) - .1) < .00001,
            "Quiet night has a 10% meteor start chance per minute");
        var daytime = new SkyEventCycle(17);
        for (int i = 0; i < 100000; i++) daytime.tick(true, false);
        check(!daytime.active(), "Meteor showers cannot start during daytime");
        boolean started = false, ended = false;
        for (int i = 0; i < 220000; i++) {
            float before = cycle.strength();
            cycle.tick(true);
            check(cycle.strength() >= 0 && cycle.strength() <= 20, "Bounded strength");
            check(Math.abs(cycle.strength() - before) <= .101F, "Automatic values ease");
            started |= cycle.active(); ended |= started && !cycle.active();
        }
        check(started && ended, "Automatic event starts and stops");
        float frozen = cycle.strength(); cycle.tick(false);
        check(frozen == cycle.strength(), "Weather gamerule freezes evolution");
        for (int start = 0; start < 3; start++) {
            for (int age = 0; age < 40; age++)
                check(MeteorShower.frame(start, 40, age) == start + ((age / 2) % 2), "Two-frame flicker");
            for (int f = start + 2; f < 7; f++)
                check(MeteorShower.frame(start, 40, 40 + 2 * (f - start - 2)) == f, "Ordered outro");
        }
        check(MeteorShower.opacity(0) == 0 && MeteorShower.opacity(6) == 1 && MeteorShower.opacity(90) == 1, "Fade in only");
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("skyevent minecraft:overworld meteor_shower 20");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set midnight");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("tp @a 0 220 0 0 -45");
            context.waitTicks(120);
            server.runCommand("skyevent status");
            server.runCommand("skyevent minecraft:overworld status");
            server.runCommand("skyevent minecraft:the_end status");
            server.runOnServer(s -> {
                check(SkyEvents.strength(s.overworld(), SkyEventType.METEOR_SHOWER) == 20, "Server override");
                check(SkyEvents.strength(s.getLevel(Level.END), SkyEventType.METEOR_SHOWER) == 0, "Meteor showers remain Overworld-only");
                check(SkyEvents.strength(s.getLevel(Level.NETHER), SkyEventType.METEOR_SHOWER) == 0, "No Nether event");
            });
            context.runOnClient(client -> {
                check(MeteorShower.strength() == 20, "Strength synchronized");
                check(MeteorShower.count() >= 5 && MeteorShower.count() <= 128, "Bounded active sprites");
            });
            context.takeScreenshot("sky-events-meteor-shower");
            server.runCommand("tp @a 0 220 0 120 -35");
            context.waitTicks(20);
            context.takeScreenshot("sky-events-meteor-shower-rotated");
            server.runCommand("skyevent minecraft:overworld meteor_shower 0");
            context.waitTicks(120);
            context.runOnClient(client -> check(MeteorShower.count() == 0, "Zero strength stops spawning; outro expires"));
            server.runCommand("skyevent minecraft:overworld meteor_shower 20");
            context.waitTicks(50);
            server.runCommand("execute in minecraft:the_end run tp @a 0 100 0");
            context.waitTicks(30);
            context.runOnClient(client -> check(MeteorShower.count() == 0 && MeteorShower.strength() == 0, "Dimension transition clears sprites"));
        }
        try (var otherWorld = context.worldBuilder().create()) {
            var server = otherWorld.getServer();
            server.runCommand("time set midnight");
            server.runOnServer(s -> {
                var settings = SkyEventWorldData.get(s).settings(s.overworld(), SkyEventType.METEOR_SHOWER);
                check(!settings.override && settings.strength == 10, "New world starts with its own sky-event settings");
                check(SkyEvents.strength(s.overworld(), SkyEventType.METEOR_SHOWER) == 0,
                    "Previous world's active meteor shower does not transfer");
            });
            context.waitTicks(20);
            context.runOnClient(client -> check(MeteorShower.strength() == 0 && MeteorShower.count() == 0,
                "Client clears the previous world's meteor visuals"));
        }
    }
}
