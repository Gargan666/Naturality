package naturality.client.ui.menus.previews;

import naturality.client.ui.menus.previews.MenuSurface.MenuFog;
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
import naturality.client.ui.menus.previews.MenuSurface.MenuSavedRegion;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
        // A stale preview reference must never disconnect a replacement gameplay server.
        if (server == null || server != client.getSingleplayerServer()) {
            disconnected();
            return;
        }
        switching = true;
        try { client.disconnect(destination, false, false); }
        finally { disconnected(); switching = false; }
    }
    /** Release client ownership after native disconnect; retiring servers keep their private save policy. */
    public static void disconnected() {
        cancelPreparation();
        server = null; connected = false; hiddenLoadingScreen = null;
        MenuWorld.camera().suspend();
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
    private static final java.util.concurrent.ConcurrentLinkedQueue<naturality.api.worlds.DisposableWorlds.Scope> temporaryRoots = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public static boolean owns(IntegratedServer candidate) { return candidate == server; }
    public static void claimServer(IntegratedServer candidate) { if (creating) { server = candidate; privateServers.put(candidate, true); naturality.api.worlds.PreviewEnvironment.register(candidate, selectionPreview); } }
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
        if (stopped) return proposed;
        if (backgroundScreen == null) {
            navigated(proposed);
            return proposed;
        }
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
        String activeCheck = System.getProperty("naturality.test.activeCheck", "");
        if (suites != null && !activeCheck.equals("menu-world") && !activeCheck.equals("menu-no-save")
                && !activeCheck.equals("menu-world-entry")) return;
        // Fabric's test scheduler must finish its own overlay/thread barrier before a test owns a server.
        if (suites != null && !attempted && client.gui.overlay() != null) return;
        // Check ownership before any cleanup or connection work can act on Minecraft's current server.
        if (server != null && server != client.getSingleplayerServer()) {
            disconnected();
            attempted = false;
            return;
        }
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
                ((naturality.client.ui.menus.previews.mixins.PreviewsMixins.MenuCreateWorldAccess)screen).naturality$uiState().setSeed("8675309");
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
            directory = Files.createTempDirectory("naturality-menu-world-"); temporaryRoots.add(naturality.api.worlds.DisposableWorlds.open(directory));
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
        for (var scope : temporaryRoots) {
            if (cleanFiles(scope.root())) { scope.close(); temporaryRoots.remove(scope); }
        }
    }
    private static boolean cleanFiles(Path root) {
        if (root == null || !root.getFileName().toString().startsWith("naturality-menu-world-")) return false;
        if (!Files.exists(root)) return true;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                if (path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) Files.deleteIfExists(path);
            return true;
        } catch (IOException error) { Naturality.LOGGER.debug("Menu preview cleanup deferred", error); return false; }
    }
    private MenuPreview() { }

    /** Disk work and resource preparation never enter a client-thread wait loop. */
    public static final class MenuPreviewLoader {
        private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "Naturality preview preparation"); thread.setDaemon(true); return thread;
        });
        public record Prepared(LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem stem) implements AutoCloseable {
            @Override public void close() {
                try { stem.close(); }
                finally { access.safeClose(); }
            }
        }
        public static CompletableFuture<Prepared> prepare(Path root, Path saved, WorldCreationContext context,
                long seed, BooleanSupplier cancelled) {
            return CompletableFuture.supplyAsync(() -> {
                LevelStorageSource.LevelStorageAccess access = null;
                PackRepository packs = null;
                try {
                    check(cancelled);
                    if (saved != null) MenuSavedRegion.copy(saved, root.resolve("worlds/preview"), cancelled);
                    check(cancelled);
                    access = LevelStorageSource.createDefault(root.resolve("worlds")).validateAndCreateAccess("preview");
                    packs = ServerPacksSource.createPackRepository(access);
                    var worldAccess = access;
                    var tag = saved == null ? null : access.getUnfixedDataTag(false);
                    var config = tag == null
                            ? new WorldLoader.PackConfig(packs, context == null ? WorldDataConfiguration.DEFAULT : context.dataConfiguration(), false, false)
                            : LevelStorageSource.getPackConfig(tag, packs, false);
                    var init = new WorldLoader.InitConfig(config, Commands.CommandSelection.INTEGRATED, LevelBasedPermissionSet.GAMEMASTER);
                    var stem = load(init, loaded -> {
                        check(cancelled);
                        if (tag != null) {
                            var data = LevelStorageSource.getLevelDataAndDimensions(worldAccess, tag, loaded.dataConfiguration(),
                                    loaded.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM), loaded.datapackWorldRegistries());
                            return new WorldLoader.DataLoadOutput<>(data.worldDataAndGenSettings(), data.dimensions().dimensionsRegistryAccess());
                        }
                        var dimensions = context == null ? WorldPresets.createNormalWorldDimensions(loaded.datapackWorldRegistries())
                                : net.minecraft.world.level.levelgen.WorldDimensions.CODEC.codec().parse(
                                        loaded.datapackWorldRegistries().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                                        net.minecraft.world.level.levelgen.WorldDimensions.CODEC.codec().encodeStart(
                                                context.worldgenLoadContext().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                                                context.selectedDimensions()).getOrThrow()).getOrThrow();
                        var baked = dimensions.bake(loaded.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
                        var settings = new LevelSettings("Naturality menu preview", GameType.SPECTATOR,
                                LevelSettings.DifficultySettings.DEFAULT, true, loaded.dataConfiguration());
                        var data = new PrimaryLevelData(settings, baked.specialWorldProperty(), baked.lifecycle());
                        var options = context == null ? new WorldOptions(seed, true, false) : context.options();
                        return new WorldLoader.DataLoadOutput<>(new LevelDataAndDimensions.WorldDataAndGenSettings(data,
                                new WorldGenSettings(options, dimensions)), baked.dimensionsRegistryAccess());
                    }).join();
                    var result = new Prepared(access, packs, stem);
                    if (cancelled.getAsBoolean()) { result.close(); throw new java.util.concurrent.CancellationException(); }
                    return result;
                } catch (Exception error) {
                    if (access != null) access.safeClose();
                    throw new java.util.concurrent.CompletionException(error);
                }
            }, IO);
        }
        /** Defer global tag/component installation until this request is accepted on the client. */
        private static CompletableFuture<WorldStem> load(WorldLoader.InitConfig config,
                WorldLoader.WorldDataSupplier<LevelDataAndDimensions.WorldDataAndGenSettings> supplier) {
            var executor = net.minecraft.util.Util.backgroundExecutor();
            var packs = config.packConfig().createResourceManager();
            var resources = packs.getSecond();
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLD), tags);
            return net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLD_REGISTRIES, executor)
                    .thenComposeAsync(world -> {
                        var dimensionLookups = java.util.stream.Stream.concat(lookups.stream(), world.listRegistries()).toList();
                        return net.minecraft.resources.RegistryDataLoader.load(resources, dimensionLookups,
                                net.minecraft.resources.RegistryDataLoader.DIMENSION_REGISTRIES, executor).thenComposeAsync(dimensions -> {
                            var loaded = supplier.get(new WorldLoader.DataLoadContext(resources, packs.getFirst(),
                                    net.minecraft.core.HolderLookup.Provider.create(dimensionLookups.stream()), dimensions));
                            var registries = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLD, world, loaded.finalDimensions());
                            return net.minecraft.server.ReloadableServerResources.loadResources(resources, registries, tags,
                                    packs.getFirst().enabledFeatures(), config.commandSelection(), config.functionCompilationPermissions(), executor, executor)
                                    .thenApply(managers -> new WorldStem(resources, managers, registries, loaded.cookie()));
                        }, executor);
                    }, executor).whenComplete((_, error) -> { if (error != null) resources.close(); });
        }
        public static void discard(CompletableFuture<Prepared> future) {
            future.thenAcceptAsync(Prepared::close, IO);
        }
        private static void check(BooleanSupplier cancelled) {
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
        }
        private MenuPreviewLoader() { }
    }

    /** Main-thread attach/connect operations, each bounded to one client tick. */
    @org.jspecify.annotations.NullMarked
    public interface MenuPreviewConnection {
        void naturality$startPreview(MenuPreviewLoader.Prepared prepared);
        void naturality$connectPreview();
    }

    /** The generated menu world uses native rendering; only orbit state is separate. */
    public static final class MenuWorld {
        private static final MenuWorldCamera CAMERA = new MenuWorldCamera();
        public static MenuWorldCamera camera() { return CAMERA; }
        public static void initialize() { MenuPreview.initialize(); }
        private MenuWorld() { }
    }

    /** A menu orbit camera; all distances are relative to the viewport. */
    public static final class MenuWorldCamera {
        private float yaw = 35, pitch = 34;
        private double seconds;
        private long lastFrame;
        private boolean held;
        private double yawVelocity, pitchVelocity;
        private long lastDrag;

        public void frame(double speed) {
            long now = System.nanoTime();
            double dt = lastFrame == 0 ? 0 : Math.clamp((now - lastFrame) / 1.0e9, 0, .1);
            lastFrame = now;
            advance(dt, speed);
        }

        public void advance(double dt, double speed) {
            seconds += dt;
            yaw = wrap(yaw + (float)(dt * speed * 3));
            if (!held) {
                double decay = Math.exp(-2.2 * dt), travel = (1 - decay) / 2.2;
                yaw = wrap(yaw + (float)(yawVelocity * travel));
                float wanted = pitch + (float)(pitchVelocity * travel);
                pitch = Math.clamp(wanted, -65, 85);
                yawVelocity *= decay;
                pitchVelocity = wanted == pitch ? pitchVelocity * decay : 0;
            }
        }
        public void hold() { held = true; lastDrag = 0; yawVelocity = pitchVelocity = 0; }
        public void release() {
            held = false;
            if (System.nanoTime() - lastDrag > 120_000_000) yawVelocity = pitchVelocity = 0;
        }
        public void suspend() { held = false; yawVelocity = pitchVelocity = 0; lastFrame = 0; }
        public void drag(double dx, double dy, int width, int height) {
            double horizontal = dx * 180 / Math.max(1, width), vertical = dy * 120 / Math.max(1, height);
            long now = System.nanoTime();
            double dt = lastDrag == 0 ? 1 / 60.0 : Math.clamp((now - lastDrag) / 1.0e9, 1 / 240.0, .1);
            double blend = lastDrag == 0 ? 1 : 1 - Math.exp(-25 * dt);
            yawVelocity += (Math.clamp(horizontal / dt, -360, 360) - yawVelocity) * blend;
            pitchVelocity += (Math.clamp(vertical / dt, -240, 240) - pitchVelocity) * blend;
            lastDrag = now;
            yaw = wrap(yaw + (float)horizontal);
            pitch = Math.clamp(pitch + (float)vertical, -65, 85);
        }
        private static float wrap(float angle) { return (angle % 360 + 360) % 360; }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
        /** Cloud blend at the level orbit: above/below starts immediately on either side.
         * The existing cap shader finishes its soft transition within three degrees. */
        public float cloudLayerPosition() {
            if (pitch == 0) return .5F;
            return pitch > 0 ? 1 + pitch / 12F : pitch / 12F;
        }
        public double seconds() { return seconds; }
        public boolean held() { return held; }
    }

    /** Native/mod sky and clouds supplied with a fixed first-day state, without a world. */
    public static final class MenuPresetSky {
        private static SkyRenderer sky;
        private static ProjectionMatrixBuffer projection;
        private static MappableRingBuffer fog;
        public static int frames;
        public static void render() {
            var client = Minecraft.getInstance();
            var screen = client.gui.screen();
            if (!client.isGameLoadFinished() || MenuPreview.stopped()) return;
            if (client.gameRenderer.gameRenderState().shouldRenderLevel) { closeSky(); return; }
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen
                    || screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
                    || screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)) return;
            var target = client.gameRenderer.mainRenderTarget();
            var colorTexture = target.getColorTexture();
            var depthTexture = target.getDepthTexture();
            var colorView = target.getColorTextureView();
            if (colorTexture == null || depthTexture == null || colorView == null) return;
            if (sky == null) sky = new SkyRenderer(client.getTextureManager(), client.getAtlasManager(), target);
            if (projection == null) projection = new ProjectionMatrixBuffer("Preset menu sky projection");
            if (fog == null) fog = new MappableRingBuffer(() -> "Preset menu sky fog", 130, FogRenderer.FOG_UBO_SIZE);
            fog.rotate();
            var color = new Vector4f(.67F, .79F, .97F, 1);
            try (var mapped = fog.currentBuffer().map(false, true)) {
                Std140Builder.intoBuffer(mapped.data()).putVec4(color).putFloat(1000000).putFloat(1000001)
                        .putFloat(1000000).putFloat(1000001).putFloat(4096).putFloat(4096);
            }
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colorTexture, color, depthTexture, 0);
            var state = new SkyRenderState();
            state.skybox = Skybox.OVERWORLD; state.skyColor = new Vector3f(.48F, .67F, 1);
            state.sunriseAndSunsetColor = new Vector4f(0, 0, 0, 0);
            state.sunAngle = 0; state.moonAngle = (float)Math.PI; state.starAngle = (float)Math.PI;
            state.rainBrightness = 1;
            var window = client.getWindow();
            var matrix = new Matrix4f().setPerspective((float)Math.toRadians(client.options.fov().get()),
                    window.getWidth() / (float)Math.max(1, window.getHeight()), 4096, .05F,
                    RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
            var view = RenderSystem.getModelViewStack();
            view.pushMatrix(); RenderSystem.backupProjectionMatrix();
            try {
                view.identity().rotateX((float)Math.toRadians(34)).rotateY((float)Math.toRadians(215));
                RenderSystem.setProjectionMatrix(projection.getBuffer(matrix), ProjectionType.PERSPECTIVE);
                RenderSystem.setShaderFog(fog.currentBuffer().slice()); MenuFog.prepare();
                sky.render(fog.currentBuffer().slice(), state);
                var clouds = client.levelRenderer.cloudRenderer();
                var status = client.options.getCloudStatus();
                if (status != net.minecraft.client.CloudStatus.OFF) {
                    clouds.prepare(0xffffffff, status, 192.33F, Math.max(36, client.options.cloudRange().get() * 4), new Vec3(0, 200, 0), 0, 0);
                    try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Preset native menu clouds",
                            colorView, Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                        RenderSystem.bindDefaultUniforms(pass); clouds.render(status, pass);
                    }
                }
                frames++;
            } finally { RenderSystem.restoreProjectionMatrix(); view.popMatrix(); }
        }
        private static void closeSky() { if (sky != null) { sky.close(); sky = null; } }
        public static void close() {
            closeSky();
            if (projection != null) { projection.close(); projection = null; }
            if (fog != null) { fog.close(); fog = null; }
        }
        private MenuPresetSky() { }
    }
}
