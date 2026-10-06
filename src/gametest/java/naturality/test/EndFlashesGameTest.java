package naturality.test;

import naturality.client.sky.EndFlashes;
import naturality.client.sky.SkyEventsClient;
import naturality.sky.SkyEventCycle;
import naturality.sky.SkyEventType;
import naturality.sky.SkyEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.Level;

public final class EndFlashesGameTest implements FabricClientGameTest {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
    @Override public void runTest(ClientGameTestContext context) {
        var normal = new EndFlashes.Pulse();
        var strong = new EndFlashes.Pulse();
        int normalTicks = 0, strongTicks = 0, small = 0, large = 0;
        float minPeak = Float.MAX_VALUE, maxPeak = 0, peak = 0;
        for (long time = 600; time < 60600; time++) {
            normal.tick(time, 10);
            strong.tick(time, 20);
            if (normal.getIntensity(1) > .00001F) normalTicks++;
            if (strong.getIntensity(1) > .00001F) strongTicks++;
            peak = Math.max(peak, strong.getIntensity(1));
            if (time % 600 == 599) {
                check(strong.size() >= .4F && strong.size() <= 2.2F, "Flash size stays within its intended range");
                if (strong.size() < 1) small++;
                if (strong.size() > 1.5F) large++;
                minPeak = Math.min(minPeak, peak); maxPeak = Math.max(maxPeak, peak); peak = 0;
            }
        }
        check(strongTicks > normalTicks * .23 && strongTicks < normalTicks * .27,
            "Strength 20 compresses fade-in and fade-out to a quarter of vanilla duration");
        check(small > large * 2 && large > 0, "Large flashes occur but are rarer than small flashes");
        check(minPeak < .9F && maxPeak > 1.1F, "Peak brightness varies across strong flashes");
        for (float initial : new float[]{10, 20}) {
            var reference = new EndFlashes.Pulse();
            var changing = new EndFlashes.Pulse();
            boolean seen = false;
            for (long time = 600; time < 1200; time++) {
                reference.tick(time, initial, .6F);
                changing.tick(time, seen ? 0 : initial, seen ? 0 : .6F);
                seen |= reference.eventIntensity(1) > 0;
                check(reference.eventIntensity(.5F) == changing.eventIntensity(.5F)
                    && reference.size() == changing.size() && reference.speed() == changing.speed(),
                    "An existing flash keeps its entire brightness, size and timing curve after strength drops to zero");
            }
            check(seen, "Continuation fixture contains a visible pulse");
            for (long time = 1200; time < 1800; time++) {
                changing.tick(time, 0, 0);
                check(changing.eventIntensity(1) == 0 && !changing.audibleStart(),
                    "Zero strength prevents subsequent flashes and sounds");
            }
        }
        double roll = SkyEventCycle.END_FLASH_CHANCE_PER_TICK;
        check(Math.abs(1 - Math.pow(1 - roll, 1200) - .2) < .00001,
            "End flashes start with 20% chance per quiet minute");
        var cycle = SkyEventCycle.endFlashes(415);
        boolean started = false, ended = false;
        for (int i = 0; i < 120000; i++) {
            cycle.tick(true);
            started |= cycle.active();
            ended |= started && !cycle.active();
            check(cycle.strength() >= 0 && cycle.strength() <= 20,
                "End event strength remains within its slider range");
        }
        check(started && ended, "Automatic End flashes start and finish");
        var restored = SkyEventCycle.endFlashes(1);
        restored.restore(cycle.snapshot());
        for (int i = 0; i < 2000; i++) {
            cycle.tick(true);
            restored.tick(true);
            check(cycle.snapshot().equals(restored.snapshot()), "Saved End cycles resume the same sequence");
        }
        check(EndFlashes.extraWeight(0, 10) == 0 && EndFlashes.power(10) == 1
            && EndFlashes.extraWeight(4, 20) == 1 && EndFlashes.power(20) > EndFlashes.power(10),
            "High strength adds five pulses and brightens vanilla flashes");
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(s -> {
                for (var level : s.getAllLevels()) level.getGameRules().set(
                    net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, true, s);
            });
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 3000 110 0");
            server.runCommand("skyevent minecraft:the_end end_flashes 0");
            context.waitTicks(30);
            server.runOnServer(s -> check(SkyEvents.strength(s.getLevel(Level.END), SkyEventType.END_FLASHES) == 0,
                "Zero override suppresses the End event"));
            context.runOnClient(client -> check(EndFlashes.strength() == 0,
                "End event reaches the client as quiet"));
            var before = new java.util.concurrent.atomic.AtomicReference<java.util.Map<String, Long>>();
            server.runOnServer(s -> {
                s.getLevel(Level.END).getGameRules().set(
                    net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER, false, s);
                before.set(naturality.weather.EnvironmentWorldData.get(s)
                    .read("sky/minecraft:the_end/end_flashes"));
            });
            context.waitTicks(5);
            server.runOnServer(s -> check(!before.get().equals(naturality.weather.EnvironmentWorldData.get(s)
                .read("sky/minecraft:the_end/end_flashes")), "End event clock advances without a weather timer"));
            server.runCommand("skyevent minecraft:the_end status");
            server.runCommand("skyevent minecraft:the_end end_flashes 10");
            context.waitTicks(20);
            context.runOnClient(client -> {
                check(client.level.endFlashState() instanceof EndFlashes.State,
                    "The event replaces the world's vanilla state for every consumer");
                var vanilla = new net.minecraft.client.renderer.EndFlashState();
                var event = new EndFlashes.State(client.level);
                int starts = 0;
                try {
                    for (long time = 600; time < 4200; time++) {
                        vanilla.tick(time);
                        event.tick(time);
                        if (vanilla.flashStartedThisTick()) starts++;
                        for (float partial : new float[]{0, .5F, 1}) {
                            check(event.getIntensity(partial) == vanilla.getIntensity(partial),
                                "Strength 10 has exactly vanilla intensity and interpolation");
                            check(event.visible(partial, 1).size() <= 1,
                                "Strength 10 never overlays a second flash");
                        }
                        check(event.getXAngle() == vanilla.getXAngle() && event.getYAngle() == vanilla.getYAngle(),
                            "Strength 10 has exactly vanilla positions across successive cycles");
                        check(event.soundsScheduled() == starts && !event.flashStartedThisTick(),
                            "Each vanilla pulse schedules one sound, with no second vanilla scheduler");
                    }
                } finally { event.stopSounds(); }
            });
            server.runCommand("skyevent minecraft:the_end end_flashes 20");
            // Strength changes no longer reveal already-started, previously disabled streams.
            context.waitTicks(635);
            server.runOnServer(s -> check(SkyEvents.strength(s.getLevel(Level.END), SkyEventType.END_FLASHES) == 20,
                "End override reaches full strength"));
            context.runOnClient(client -> check(SkyEventsClient.strength(client.level, SkyEventType.END_FLASHES) == 20,
                "End strength synchronizes to the client"));
            context.waitFor(client -> EndFlashes.visible(0, 1).stream()
                .anyMatch(flash -> flash.intensity() > .7F));
            context.runOnClient(client -> {
                var flashes = EndFlashes.visible(0, 1);
                check(flashes.stream().map(f -> f.xAngle() + ":" + f.yAngle()).distinct().count() == flashes.size(),
                    "Additional flashes use distinct seed positions");
                float visible = (float)flashes.stream().mapToDouble(EndFlashes.Flash::lightIntensity).max().orElse(0);
                check(Math.abs(client.level.endFlashState().getIntensity(0) - visible) < .00001F,
                    "World light intensity follows the strongest actual event flash");
                var extractor = new net.minecraft.client.renderer.LightmapRenderStateExtractor(client.gameRenderer, client);
                var light = new net.minecraft.client.renderer.state.LightmapRenderState();
                boolean hidden = client.options.hideLightningFlash().get();
                try {
                    client.options.hideLightningFlash().set(true);
                    extractor.tick(); extractor.extract(light, 0);
                    float without = light.skyFactor;
                    client.options.hideLightningFlash().set(false);
                    extractor.tick(); extractor.extract(light, 0);
                    float factor = naturality.client.lighting.HardcoreDarkness.atmosphere(client.level, client.gameRenderer.mainCamera(), 0);
                    check(Math.abs(light.skyFactor - without - visible * factor) < .0001F,
                        "Real lightmap receives event intensity and respects Hide Sky Flashes");
                } finally { client.options.hideLightningFlash().set(hidden); }
            });
            float[] heading = new float[2];
            context.runOnClient(client -> {
                var flash = EndFlashes.visible(0, 1).stream()
                    .max(java.util.Comparator.comparingDouble(EndFlashes.Flash::intensity)).orElseThrow();
                heading[0] = flash.yAngle();
                heading[1] = flash.xAngle();
            });
            server.runCommand("execute in minecraft:the_end run tp @a 3000 110 0 " + heading[0] + " " + heading[1]);
            context.waitTicks(4);
            context.runOnClient(client -> check(client.level.dimension().equals(Level.END),
                "Visual captures stay in the End"));
            context.runOnClient(client -> check(EndFlashes.drawnFlashes() > 0,
                "Active End flashes draw through vanilla's sky renderer"));
            context.takeScreenshot("end-flashes-strength-20");
            server.runCommand("skyevent minecraft:the_end end_flashes 0");
            context.waitTicks(400);
            context.runOnClient(client -> check(EndFlashes.visible(0, 1).isEmpty() && EndFlashes.drawnFlashes() == 0,
                "Zero strength stops new flashes after existing pulses finish"));
            context.runOnClient(client -> check(client.level.endFlashState().getIntensity(0) == 0
                && client.level.endFlashState().getIntensity(1) == 0,
                "Zero strength also removes the vanilla terrain-light pulse"));
            context.takeScreenshot("end-flashes-strength-0");
            server.runCommand("skyevent minecraft:the_end end_flashes auto");
        }
    }
}
