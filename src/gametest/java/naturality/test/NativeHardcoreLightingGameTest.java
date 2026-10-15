package naturality.test;

import naturality.client.lighting.HardcoreDarkness;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.world.level.MoonPhase;

/** Rendering-only regression, independent of configuration-screen organization. */
public final class NativeHardcoreLightingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -8 100 -8 8 100 8 smooth_quartz");
            server.runCommand("tp @a 0 102 0 0 -25");
            for (int phase = 0; phase <= 4; phase++) {
                server.runCommand("time set " + (phase * 24000 + 18000));
                world.getConnection().waitForClientboundPackets(); context.waitTicks(25);
                final int selected = phase;
                context.runOnClient(client -> {
                    var settings = NaturalityConfig.get().hardcoreDarkness;
                    float expected = settings.moon.enabled && settings.moon.dimensions.includes(client.level.dimension())
                            ? HardcoreDarkness.moonBrightness(MoonPhase.values()[selected]) : 1;
                    float actual = HardcoreDarkness.atmosphere(client.level, client.gameRenderer.mainCamera(), 0);
                    if (Math.abs(actual - expected) > .001F) throw new AssertionError("Moonlight multiplier: " + actual + " != " + expected);
                    var extractor = new LightmapRenderStateExtractor(client.gameRenderer, client);
                    extractor.tick(); var state = new LightmapRenderState(); extractor.extract(state, 0);
                    if (expected == 0 && state.skyFactor != 0) throw new AssertionError("New moon leaves skylight");
                    if (HardcoreDarkness.cavesEnabled(client.level) && settings.caves.ambientPercent == 0
                            && state.ambientColor.lengthSquared() != 0) throw new AssertionError("Disabled ambient light remains");
                    if (naturality.client.menu.MenuPreview.active()) throw new AssertionError("Gameplay inherited the preview camera");
                    System.out.println("NATIVE_LIGHTING phase=" + selected + " multiplier=" + actual + " sky=" + state.skyFactor);
                });
            }
        }
    }
}
