package naturality.test;

import java.nio.file.Path;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Reproducible chunk-arrival CPU profile; lockstep test tick times are not gameplay FPS. */
public final class OverworldLoadingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int distance = context.computeOnClient(c -> c.options.renderDistance().get());
        try (var recording = new Recording(Configuration.getConfiguration("profile"))) {
            recording.start();
            try (var world = context.worldBuilder().setUseConsistentSettings(false)
                    .adjustSettings(s -> s.setSeed("84219871")).create()) {
                var server = world.getServer();
                server.runCommand("gamemode spectator @a");
                server.runCommand("tp @a 52 111 -300 90 6");
                server.runCommand("time set 6000");
                server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
                context.runOnClient(c -> c.options.renderDistance().set(32));
                context.waitTicks(180);
                context.takeScreenshot("overworld-32-arrival");
                context.runOnClient(c -> System.out.println("OVERWORLD_32 arrival: " + c.levelExtractor.sectionStatistics()));
                context.waitTicks(180);
                context.takeScreenshot("overworld-32-stationary");
                context.runOnClient(c -> System.out.println("OVERWORLD_32 stationary: " + c.levelExtractor.sectionStatistics()));
                server.runCommand("tp @a 340 111 -300 90 6");
                context.waitTicks(180);
                context.takeScreenshot("overworld-32-travel");
                context.runOnClient(c -> System.out.println("OVERWORLD_32 travel: " + c.levelExtractor.sectionStatistics()));
            }
            recording.stop();
            recording.dump(Path.of("overworld-loading.jfr"));
        } catch (Exception e) { throw new AssertionError(e); }
        finally { context.runOnClient(c -> c.options.renderDistance().set(distance)); }
    }
}
