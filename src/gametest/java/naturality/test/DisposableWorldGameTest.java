package naturality.test;

import java.nio.file.Files;
import java.nio.file.Path;
import naturality.client.menu.MenuPreview;
import naturality.util.DisposableWorlds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Independently selected contracts: discard writes, opt-in persistence, and production menu integration. */
public final class DisposableWorldGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        Path root;
        try (var world = TestWorlds.disposable(context).create()) {
            root = world.directory();
            if (!DisposableWorlds.discards(root) || DisposableWorlds.discards(root.resolveSibling(root.getFileName() + "-other")))
                throw new AssertionError("Disposable registration must be scoped to exactly this root");
            world.getServer().runCommand("setblock 0 100 0 gold_block");
            world.getServer().runCommand("summon pig 0 101 0 {NoAI:1b}");
            world.getServer().runCommand("setblock 2 100 0 lectern");
            world.getServer().runOnServer(server -> {
                if (!DisposableWorlds.discards(server)) throw new AssertionError("Server missed its disposable registration");
                server.saveEverything(false, true, true);
                server.getPlayerList().saveAll();
                server.overworld().save(null, true, false);
                server.overworld().getChunkSource().save(true);
                server.getDataStorage().saveAndJoin();
                server.overworld().getDataStorage().saveAndJoin();
                if (!server.overworld().getBlockState(new BlockPos(0, 100, 0)).is(Blocks.GOLD_BLOCK))
                    throw new AssertionError("Suppressing saves must not discard the live world");
            });
            noSavedFiles(root);
            try { world.getWorldSave(); throw new AssertionError("Disposable world offered a reopen handle"); }
            catch (IllegalStateException expected) { /* Explicit persistence is required. */ }
        }
        noSavedFiles(root);
        if (DisposableWorlds.discards(root)) throw new AssertionError("Closed test leaked its registration");
    }

    static void persistence(ClientGameTestContext context) {
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        try (var world = TestWorlds.persistent(context).create()) {
            save = world.getWorldSave();
            world.getServer().runOnServer(server -> {
                if (DisposableWorlds.discards(server)) throw new AssertionError("Persistent world inherited a discard policy");
                server.overworld().setBlockAndUpdate(new BlockPos(0, 100, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
                server.saveEverything(false, true, true);
            });
        }
        if (!Files.exists(save.getSaveDirectory().resolve("level.dat"))) throw new AssertionError("Persistent world did not save metadata");
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(server -> {
                if (!server.overworld().getBlockState(new BlockPos(0, 100, 0)).is(Blocks.DIAMOND_BLOCK))
                    throw new AssertionError("Opt-in world failed its actual disk round-trip");
            });
        }
    }

    static void menu(ClientGameTestContext context) {
        context.runOnClient(client -> {
            MenuPreview.stop(client);
            client.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        });
        context.waitFor(_ -> MenuPreview.ready(), 2400);
        var server = TestFixtures.require(context.computeOnClient(client -> client.getSingleplayerServer()), "menu server");
        Path root = server.getWorldPath(LevelResource.ROOT);
        try {
            if (!DisposableWorlds.discards(root)) throw new AssertionError("Menu preview did not use the shared API");
            noSavedFiles(root);
        } finally { context.runOnClient(MenuPreview::stop); }
        context.waitFor(_ -> server.isShutdown(), 1200);
        noSavedFiles(root);
    }

    private static void noSavedFiles(Path root) {
        try (var files = Files.walk(root)) {
            var saved = files.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals("session.lock")).toList();
            if (!saved.isEmpty()) throw new AssertionError("Disposable world wrote files: " + saved);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
