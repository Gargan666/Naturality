package naturality.client.menu;

import java.nio.file.Path;
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

/** Disk work and resource preparation never enter a client-thread wait loop. */
public final class MenuPreviewLoader {
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
