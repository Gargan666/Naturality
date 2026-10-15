package naturality.test;

import naturality.client.ui.menus.previews.MenuPreview;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Native disconnect must release preview ownership before the next gameplay world attaches. */
public final class MenuWorldEntryGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.gui.setScreen(new TitleScreen()));
        context.waitFor(_ -> MenuPreview.ready(), 2400);
        var preview = TestFixtures.require(context.computeOnClient(client -> client.getSingleplayerServer()), "preview server");
        context.runOnClient(client -> {
            // Use Minecraft's normal handoff, not MenuPreview.stop(), which used to mask the stale reference.
            client.disconnect(new GenericMessageScreen(Component.literal("Entering test world")), false, false);
            if (MenuPreview.owns(preview) || MenuPreview.active() || MenuPreview.starting())
                throw new AssertionError("Native disconnect retained preview ownership before gameplay loading");
            if (client.level != null || client.getSingleplayerServer() != null)
                throw new AssertionError("Native disconnect did not detach the preview");
        });
        // Fabric's test scheduler supports only one server thread at a time.
        context.waitFor(_ -> preview.isShutdown(), 1200);
        try (var world = TestWorlds.disposable(context).create()) {
            world.getConnection().waitForClientboundPackets();
            var gameplay = TestFixtures.require(context.computeOnClient(client -> client.getSingleplayerServer()), "gameplay server");
            int initialTick = TestFixtures.require(world.getServer().computeOnServer(server -> server.getTickCount()), "initial gameplay tick");
            context.waitTicks(20);
            context.runOnClient(client -> {
                TestFixtures.level(client);
                TestFixtures.player(client);
                if (client.getSingleplayerServer() != gameplay || gameplay == preview || gameplay.isShutdown())
                    throw new AssertionError("Preview cleanup stopped or replaced the gameplay server");
                if (MenuPreview.active() || MenuPreview.owns(gameplay) || MenuPreview.privateServer(gameplay))
                    throw new AssertionError("Gameplay inherited preview ownership");
                if (client.gui.screen() != null)
                    throw new AssertionError("Gameplay remained on a loading/menu screen");
            });
            world.getServer().runOnServer(server -> {
                if (server.getTickCount() <= initialTick)
                    throw new AssertionError("Gameplay server did not continue ticking after the preview handoff");
            });
        }
    }
}
