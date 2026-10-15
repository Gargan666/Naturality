package naturality.client.menu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import naturality.Naturality;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

/** A private, temporary native world, using the same rendering and environment as gameplay. */
public final class MenuPreview {
    private static Path directory;
    private static volatile IntegratedServer server;
    private static final java.util.Map<IntegratedServer, Boolean> privateServers = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static boolean starting, attempted, ready, stopped, creating, switching;
    private static volatile long generation;
    private static java.util.concurrent.CompletableFuture<MenuPreviewLoader.Prepared> preparation;
    private static Request loading;
    private static boolean connected;
    public static boolean temporaryPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return temporaryRoots.stream().anyMatch(root -> normalized.startsWith(root.toAbsolutePath().normalize()));
    }
    public static void cancelPreparation() {
        generation++;
        pending = null; loading = null; starting = ready = false;
        if (preparation != null) {
            MenuPreviewLoader.discard(preparation);
            preparation = null;
        }
    }
    private static void discard(Minecraft client, Screen destination) {
        cancelPreparation();
        switching = true;
        try { client.disconnect(destination, false, false); }
        finally { server = null; switching = false; connected = false; hiddenLoadingScreen = null; }
    }
    /** Observe real menu navigation, never replace a user's destination with an old screen. */
    public static void navigated(Screen destination) {
        if (switching || destination == null) return;
        if (destination == backgroundScreen) return;
        backgroundScreen = destination;
        if (destination instanceof TitleScreen && !ready) attempted = false;
        if (loading != null && destination != loading.screen) cancelPreparation();
        if (pending != null && destination != pending.screen) pending = null;
    }
    private static boolean preparedDuringStartup;
    private static Screen backgroundScreen;
    private record Request(Screen screen, String saved, net.minecraft.client.gui.screens.worldselection.WorldCreationContext context, String key) { }
    private static Request pending;
    private static String currentKey = "main";
    private static long requestTime;
    private static boolean selectionPreview;
    private static int selectionCheckStage;
    private static long selectionCheckClock, selectionCheckTime;
    public static boolean frozen() { return selectionPreview; }
    public static void requestSaved(Screen screen, String id) { request(new Request(screen, id, null, "saved:" + id)); }
    public static void requestSeed(Screen screen, net.minecraft.client.gui.screens.worldselection.WorldCreationUiState state) {
        var context = state.getSettings();
        request(new Request(screen, null, context, "seed:" + context.options().seed() + ":" + context.selectedDimensions().hashCode()));
    }
    private static void request(Request request) {
        if (Minecraft.getInstance().gui.screen() != request.screen || stopped) return;
        backgroundScreen = request.screen;
        selectionPreview = true;
        if ((ready || loading != null) && request.key.equals(currentKey) || pending != null && pending.key.equals(request.key)) return;
        if (loading != null) cancelPreparation();
        pending = request; requestTime = System.nanoTime();
        Naturality.LOGGER.info("Requested menu preview {}", request.key);
    }
    private static Screen hiddenLoadingScreen;
    private static volatile BlockPos spawn = BlockPos.ZERO;
    private static volatile net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> previewDimension = net.minecraft.world.level.Level.OVERWORLD;
    private static boolean dimensionTransferQueued;
    private static long seed;
    private static final java.util.concurrent.ConcurrentLinkedQueue<Path> temporaryRoots = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public static boolean owns(IntegratedServer candidate) { return candidate == server; }
    public static void claimServer(IntegratedServer candidate) { if (creating) { server = candidate; privateServers.put(candidate, true); naturality.weather.PreviewEnvironment.register(candidate, selectionPreview); } }
    public static boolean privateServer(@org.jspecify.annotations.Nullable IntegratedServer candidate) { return candidate != null && privateServers.containsKey(candidate); }
    public static boolean creating() { return creating; }
    public static boolean preparingOwnWorld() {
        return starting && (creating || server != null && server == Minecraft.getInstance().getSingleplayerServer());
    }
    public static float radius() {
        return Math.min(96, Math.max(24, Minecraft.getInstance().options.renderDistance().get() * 16 - 16));
    }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MenuPreview::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> { cancelPreparation(); stopped = true; MenuFog.close(); MenuPresetSky.close(); });
        Runtime.getRuntime().addShutdownHook(new Thread(MenuPreview::cleanFiles, "Naturality menu preview cleanup"));
    }
    public static boolean active() {
        var client = Minecraft.getInstance();
        return !stopped && server != null && server == client.getSingleplayerServer() && client.level != null;
    }
    public static boolean ready() { return ready && active(); }
    public static BlockPos spawn() { return spawn; }
    public static long seed() { return seed; }
    public static boolean starting() { return starting; }
    public static boolean stopped() { return stopped; }
    public static Matrix4f projection() {
        return projection(true);
    }
    public static Matrix4f skyProjection() {
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        return new Matrix4f().setPerspective((float)Math.toRadians(client.gameRenderer.mainCamera().getFov()),
                window.getWidth() / (float)Math.max(1, window.getHeight()), 1024, .05F,
                com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
    }
    public static Matrix4f cullingProjection() { return projection(false); }
    private static Matrix4f projection(boolean reversed) {
        var window = Minecraft.getInstance().getWindow();
        float aspect = window.getWidth() / (float)Math.max(1, window.getHeight());
        float halfHeight = Math.max(radius() * .8F, radius() / (Math.max(.4F, aspect) * .6F));
        return new Matrix4f().setOrtho(-halfHeight * aspect, halfHeight * aspect, -halfHeight, halfHeight,
                reversed ? 1024 : -1024, reversed ? -1024 : 1024,
                com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
    }
    public static Screen loadingScreen(Screen proposed) {
        if (stopped || backgroundScreen == null) return proposed;
        if (proposed == null && (preparingOwnWorld() || active() || switching)) return backgroundScreen;
        if ((preparingOwnWorld() || switching) && proposed != null) {
            String name = proposed.getClass().getSimpleName();
            if (name.equals("ReceivingLevelScreen") || name.equals("LevelLoadingScreen")) {
                hiddenLoadingScreen = proposed;
                return backgroundScreen;
            }
        }
        navigated(proposed);
        return proposed;
    }
    private static void tick(Minecraft client) {
        if (stopped) return;
        // Existing non-menu test suites retain their own world ownership.
        String suites = System.getProperty("naturality.test.suites");
        if (suites != null && !java.util.Arrays.asList(suites.split(",")).contains("menu-world")) return;
        // Fabric's test scheduler must finish its own overlay/thread barrier before a test owns a server.
        if (suites != null && !attempted && client.gui.overlay() != null) return;
        if (loading != null && client.gui.screen() != loading.screen) {
            if (server != null && client.getSingleplayerServer() == server) discard(client, client.gui.screen());
            else cancelPreparation();
        }
        if (server != null && !ready && loading == null && pending == null) {
            discard(client, client.gui.screen());
        }
        if (pending != null && System.nanoTime() - requestTime > 650_000_000L) {
            if (client.gui.screen() != pending.screen) pending = null;
            else {
                var request = pending;
                if (server != null && client.getSingleplayerServer() == server) discard(client, request.screen);
                else cancelPreparation();
                if (client.level == null && client.getSingleplayerServer() == null) launch(client, request);
            }
        }
        if (!attempted && client.level == null && client.getSingleplayerServer() == null && client.gui.screen() instanceof TitleScreen menu) {
            preparedDuringStartup = client.gui.overlay() instanceof net.minecraft.client.gui.screens.LoadingOverlay;
            launch(client, new Request(menu, null, null, "main"));
        }
        if (preparation != null && preparation.isDone()) {
            var result = preparation; preparation = null;
            try {
                var prepared = result.join(); // Already complete; never blocks the client.
                if (loading == null || client.gui.screen() != loading.screen || client.getSingleplayerServer() != null || client.level != null) MenuPreviewLoader.discard(result);
                else {
                    creating = true;
                    try { ((MenuPreviewConnection)client).naturality$startPreview(prepared); }
                    finally { creating = false; }
                }
            } catch (java.util.concurrent.CompletionException error) {
                starting = false; loading = null;
                if (!(error.getCause() instanceof java.util.concurrent.CancellationException))
                    Naturality.LOGGER.error("Could not prepare menu preview", error);
            }
        }
        if (server != null && server.isShutdown() && server == client.getSingleplayerServer()) {
            discard(client, client.gui.screen());
            return;
        }
        if (server != null && !connected && server.isReady() && loading != null) {
            configureServer(server, loading);
            ((MenuPreviewConnection)client).naturality$connectPreview();
            connected = true;
        }
        if (server != null && server != client.getSingleplayerServer()) {
            server = null; ready = starting = false;
            attempted = false;
            MenuWorld.camera().suspend();
            return;
        }
        var level = client.level;
        var player = client.player;
        if (!active() || level == null) return;
        if (player != null && !level.dimension().equals(previewDimension)) {
            if (!dimensionTransferQueued) {
                dimensionTransferQueued = true;
                var previewServer = server;
                var id = player.getUUID(); var dimension = previewDimension; var target = spawn;
                previewServer.execute(() -> {
                    var previewPlayer = previewServer.getPlayerList().getPlayer(id); var previewLevel = previewServer.getLevel(dimension);
                    if (previewPlayer != null && previewLevel != null) previewPlayer.teleportTo(previewLevel, target.getX() + .5, target.getY() + 2, target.getZ() + .5,
                            java.util.Set.of(), 0, 0, true);
                });
            }
            return;
        }
        if (client.gui.screen() instanceof TitleScreen && selectionPreview && pending == null) {
            selectionPreview = false;
            var menu = client.gui.screen();
            backgroundScreen = menu;
            pending = new Request(menu, null, null, "main"); requestTime = System.nanoTime();
        }
        if (!selectionPreview && client.gui.screen() instanceof TitleScreen && ready) {
            var previewServer = server;
            previewServer.execute(() -> previewServer.getCommands().performPrefixedCommand(previewServer.createCommandSourceStack().withSuppressedOutput(), "time add 8"));
        }
        if (hiddenLoadingScreen != null) {
            var screen = hiddenLoadingScreen;
            hiddenLoadingScreen = null;
            screen.tick();
        }
        if (!ready && client.player != null && surfaceRegionReady(client)) {
            starting = false; ready = true; loading = null;
            Naturality.LOGGER.info("Menu surface region and meshes ready; prepared during startup: {}", preparedDuringStartup);
            if (client.gui.screen() == null) client.gui.setScreen(backgroundScreen);
        }
        if (ready && client.gui.overlay() == null && Boolean.getBoolean("naturality.menu.startupCheck")) {
            if (!client.gui.canInterruptScreen()) throw new IllegalStateException("Preview blocked menu navigation");
            Naturality.LOGGER.info("Menu startup check passed: preview ready with menu navigation available");
            client.stop();
        }
        if (ready && client.gui.overlay() == null && Boolean.getBoolean("naturality.menu.selectionCheck")) {
            if (selectionCheckStage == 0) {
                selectionCheckStage = 1;
                net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(client, () -> client.gui.setScreen(new TitleScreen()));
            } else if (selectionCheckStage == 1 && client.gui.screen() instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen screen) {
                selectionCheckStage = 2;
                ((naturality.client.mixin.MenuCreateWorldAccess)screen).naturality$uiState().setSeed("8675309");
            } else if (selectionCheckStage == 2 && seed == 8675309 && frozen()) {
                selectionCheckStage = 3; selectionCheckTime = System.nanoTime();
            } else if (selectionCheckStage == 3 && System.nanoTime() - selectionCheckTime > 250_000_000L) {
                selectionCheckStage = 4; selectionCheckClock = level.getGameTime(); selectionCheckTime = System.nanoTime();
            } else if (selectionCheckStage == 4 && System.nanoTime() - selectionCheckTime > 1_000_000_000L) {
                if (level.getGameTime() != selectionCheckClock) throw new IllegalStateException("Selection preview clock advanced");
                Naturality.LOGGER.info("Menu selection check passed: preview switched to exact seed and remained frozen");
                client.stop();
            }
        }
    }
    private static void launch(Minecraft client, Request request) {
        attempted = true; starting = true; ready = false; connected = false;
        loading = request;
        backgroundScreen = request.screen; selectionPreview = !request.key.equals("main"); currentKey = request.key;
        previewDimension = net.minecraft.world.level.Level.OVERWORLD; dimensionTransferQueued = false;
        seed = request.context == null ? ThreadLocalRandom.current().nextLong() : request.context.options().seed();
        long token = ++generation;
        try {
            directory = Files.createTempDirectory("naturality-menu-world-"); temporaryRoots.add(directory);
            Path saved = null;
            if (request.saved != null) {
                var base = client.getLevelSource().getBaseDir().toAbsolutePath().normalize();
                saved = base.resolve(request.saved).normalize();
                if (!saved.startsWith(base)) throw new IOException("Invalid world preview path");
            }
            preparation = MenuPreviewLoader.prepare(directory, saved, request.context, seed, () -> generation != token || stopped);
        } catch (Exception error) {
            starting = false; loading = null;
            Naturality.LOGGER.error("Could not prepare selected world preview", error);
        }
    }
    private static void configureServer(IntegratedServer previewServer, Request request) {
        previewServer.execute(() -> {
            if (server != previewServer) return;
            previewServer.getPlayerList().setViewDistance(8); previewServer.getPlayerList().setSimulationDistance(2);
            previewServer.tickRateManager().setFrozen(!request.key.equals("main"));
            seed = previewServer.getWorldGenSettings().options().seed();
            spawn = previewServer.overworld().getRespawnData().pos();
            previewDimension = previewServer.overworld().getRespawnData().dimension();
            previewServer.overworld().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, null);
            previewServer.getCommands().performPrefixedCommand(previewServer.createCommandSourceStack(), "gamerule minecraft:advance_time false");
            if (request.saved == null) previewServer.getCommands().performPrefixedCommand(previewServer.createCommandSourceStack(), "time set 6000");
        });
    }
    private static boolean surfaceRegionReady(Minecraft client) {
        var level = client.level;
        if (level == null) return false;
        int cx = spawn.getX() >> 4, cz = spawn.getZ() >> 4;
        for (int z = cz - 7; z <= cz + 7; z++) for (int x = cx - 7; x <= cx + 7; x++)
            if (containsChunk(x, z) && !level.getChunkSource().hasChunk(x, z)) return false;
        var visible = client.levelRenderer.visibleSections();
        long fade = net.minecraft.util.Util.toMillis(client.options.chunkSectionFadeInTime().get());
        return !visible.isEmpty() && visible.stream().allMatch(section ->
                section.getSectionMesh() != net.minecraft.client.renderer.chunk.CompiledSectionMesh.UNCOMPILED
                && section.getVisibility(net.minecraft.util.Util.getMillis(), fade) >= 1);
    }
    public static boolean containsChunk(int x, int z) {
        return containsChunk(x, z, spawn);
    }
    public static boolean containsChunk(int x, int z, BlockPos center) {
        double dx = Math.max(x * 16, Math.min(center.getX(), x * 16 + 16)) - center.getX();
        double dz = Math.max(z * 16, Math.min(center.getZ(), z * 16 + 16)) - center.getZ();
        double surfaceRadius = radius() + 4; // Fully fogged source faces for the outward cover.
        return dx * dx + dz * dz < surfaceRadius * surfaceRadius;
    }
    public static void stop(Minecraft client) {
        attempted = true;
        discard(client, new TitleScreen());
    }
    private static void cleanFiles() {
        for (Path root : temporaryRoots) cleanFiles(root);
    }
    private static void cleanFiles(Path root) {
        if (root == null || !root.getFileName().toString().startsWith("naturality-menu-world-")) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                if (path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) Files.deleteIfExists(path);
        } catch (IOException error) { Naturality.LOGGER.debug("Menu preview cleanup deferred", error); }
    }
    private MenuPreview() { }
}
