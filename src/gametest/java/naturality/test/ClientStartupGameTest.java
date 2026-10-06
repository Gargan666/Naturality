package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

/** Resource presence on the Java classpath is not enough: Fabric must expose it as mod assets. */
public final class ClientStartupGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var mod = FabricLoader.getInstance().getModContainer("naturality").orElseThrow();
            for (String shader : new String[] {"clouds.vsh", "end_portal_wall.vsh", "water_intersection.vsh",
                    "portal_opening.vsh", "rain_particle.fsh", "fire_fade.vsh", "rainbow.vsh",
                    "water_particle.fsh", "end_fog.fsh", "water_immersion_overlay.fsh", "portal_overlay.fsh", "aurora.vsh"}) {
                String path = "shaders/core/" + shader;
                if (mod.findPath("assets/naturality/" + path).isEmpty())
                    throw new AssertionError("Fabric mod roots omit shader: " + shader);
                if (client.getResourceManager().getResource(Identifier.fromNamespaceAndPath("naturality", path)).isEmpty())
                    throw new AssertionError("Minecraft resource manager cannot load shader: " + shader);
            }
            client.gui.setScreen(new TitleScreen());
        });
        context.waitTicks(10);
        context.runOnClient(client -> {
            if (!(client.gui.screen() instanceof TitleScreen) || client.gui.overlay() != null)
                throw new AssertionError("Client must finish resource loading and render the title screen");
        });
        context.takeScreenshot("client-startup-title-screen");
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (client.level == null) throw new AssertionError("Client must load and render a world");
            });
        }
    }
}
