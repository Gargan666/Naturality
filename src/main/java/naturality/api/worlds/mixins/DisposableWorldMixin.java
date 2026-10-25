package naturality.api.worlds.mixins;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import naturality.api.worlds.DisposableWorlds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One set of storage hooks; nested mixins are separate targets, all governed by the same explicit policy. */
@org.jspecify.annotations.NullMarked
public final class DisposableWorldMixin {
    @Mixin(MinecraftServer.class)
    public abstract static class Server {
        @Inject(method = {"saveEverything", "saveAllChunks"}, at = @At("HEAD"), cancellable = true)
        private void naturality$discard(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
            if (DisposableWorlds.discards((MinecraftServer)(Object)this)) cir.setReturnValue(false);
        }
    }

    @Mixin(ServerLevel.class)
    public abstract static class Level {
        @Inject(method = "<init>", at = @At("RETURN"))
        @SuppressWarnings("resource") // Alias of the server-owned level being constructed; this hook must not close it.
        private void naturality$markDisposable(CallbackInfo ci) {
            var level = (ServerLevel)(Object)this;
            if (DisposableWorlds.discards(level.getServer())) level.noSave = true;
        }
        @Inject(method = "save", at = @At("HEAD"), cancellable = true)
        private void naturality$discard(CallbackInfo ci) {
            if (DisposableWorlds.discards(((ServerLevel)(Object)this).getServer())) ci.cancel();
        }
    }

    @Mixin(net.minecraft.server.level.ServerChunkCache.class)
    public abstract static class ChunkCache {
        @SuppressWarnings("null") @Shadow @Final private ServerLevel level; // Injected by Mixin.
        @Inject(method = "save", at = @At("HEAD"), cancellable = true)
        private void naturality$discard(boolean flush, CallbackInfo ci) {
            if (DisposableWorlds.discards(level.getServer())) ci.cancel();
        }
    }

    @Mixin(net.minecraft.server.level.ChunkMap.class)
    public abstract static class Chunks {
        @SuppressWarnings("null") @Shadow @Final private ServerLevel level; // Injected by Mixin.
        @Inject(method = "save", at = @At("HEAD"), cancellable = true)
        private void naturality$discard(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
            if (DisposableWorlds.discards(level.getServer())) {
                chunk.tryMarkSaved(); // Do not repeatedly schedule serialization of discarded dirty chunks.
                cir.setReturnValue(false);
            }
        }
    }

    @Mixin(net.minecraft.world.level.chunk.storage.EntityStorage.class)
    public abstract static class Entities {
        @SuppressWarnings("null") @Shadow @Final private ServerLevel level; // Injected by Mixin.
        @Inject(method = "storeEntities", at = @At("HEAD"), cancellable = true)
        private void naturality$discard(CallbackInfo ci) {
            if (DisposableWorlds.discards(level.getServer())) ci.cancel();
        }
    }

    @Mixin(net.minecraft.server.players.PlayerList.class)
    public abstract static class Players {
        @SuppressWarnings("null") @Shadow @Final private MinecraftServer server; // Injected by Mixin.
        @Inject(method = "save", at = @At("HEAD"), cancellable = true)
        private void naturality$discard(CallbackInfo ci) {
            if (DisposableWorlds.discards(server)) ci.cancel();
        }
    }

    @Mixin(net.minecraft.world.level.storage.SavedDataStorage.class)
    public abstract static class Data {
        @SuppressWarnings("null") @Shadow @Final private Path dataFolder; // Injected by Mixin.
        @Inject(method = "scheduleSave", at = @At("HEAD"), cancellable = true)
        private void naturality$discard(CallbackInfoReturnable<CompletableFuture<?>> cir) {
            if (DisposableWorlds.discards(dataFolder)) cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }

    @Mixin(LevelStorageSource.LevelStorageAccess.class)
    public abstract static class Metadata {
        @Inject(method = "getIconFile", at = @At("HEAD"), cancellable = true)
        private void naturality$discardIcon(CallbackInfoReturnable<java.util.Optional<Path>> cir) {
            if (DisposableWorlds.discards(((LevelStorageSource.LevelStorageAccess)(Object)this).getLevelPath(LevelResource.ROOT)))
                cir.setReturnValue(java.util.Optional.empty());
        }
        @Inject(method = {"saveDataTag(Lnet/minecraft/world/level/storage/WorldData;Ljava/util/UUID;)V",
                "saveLevelData(Lcom/mojang/serialization/Dynamic;)V", "saveLevelData(Lnet/minecraft/nbt/CompoundTag;)V"},
                at = @At("HEAD"), cancellable = true)
        private void naturality$discard(CallbackInfo ci) {
            if (DisposableWorlds.discards(((LevelStorageSource.LevelStorageAccess)(Object)this).getLevelPath(LevelResource.ROOT))) ci.cancel();
        }
    }

    /** Final write barrier for terrain, entities and POI, including work scheduled by vanilla close/unload. */
    @Mixin(net.minecraft.world.level.chunk.storage.IOWorker.class)
    public abstract static class Regions {
        @Unique private boolean naturality$discard;
        @Inject(method = "<init>", at = @At("RETURN"))
        private void naturality$identify(net.minecraft.world.level.chunk.storage.RegionStorageInfo info, Path directory, boolean sync, CallbackInfo ci) {
            naturality$discard = DisposableWorlds.discards(directory);
        }
        @Inject(method = "store(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
                at = @At("HEAD"), cancellable = true)
        private void naturality$discard(ChunkPos pos, Supplier<CompoundTag> data, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
            if (naturality$discard) cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }

    /** C2ME overrides IOWorker stores and adds raw writes, bypassing the inherited method barrier. */
    @Pseudo
    @Mixin(targets = "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageVanillaInterface", remap = false)
    public abstract static class C2MERegions {
        @Unique private boolean naturality$discard;
        @Inject(method = "<init>", at = @At("RETURN"))
        private void naturality$identify(net.minecraft.world.level.chunk.storage.RegionStorageInfo info, Path directory, boolean sync,
                @Nullable LongFunction<Executor> executor, CallbackInfo ci) {
            naturality$discard = DisposableWorlds.discards(directory);
        }
        @Inject(method = {
                "store(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;",
                "store(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
                "setRawChunkData(Lnet/minecraft/world/level/ChunkPos;[B)Ljava/util/concurrent/CompletableFuture;",
                "setRawChunkData(Lnet/minecraft/world/level/ChunkPos;Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;"
            }, at = @At("HEAD"), cancellable = true)
        private void naturality$discard(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
            if (naturality$discard) cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }

    @Mixin(targets = "net.fabricmc.fabric.impl.registry.sync.validate.RegistryCustomContentState", remap = false)
    public abstract static class FabricRegistry {
        @Inject(method = "writeIfNeeded", at = @At("HEAD"), cancellable = true)
        private static void naturality$discard(LevelStorageSource.LevelStorageAccess access, net.minecraft.core.RegistryAccess registries, CallbackInfo ci) {
            if (DisposableWorlds.discards(access.getLevelPath(LevelResource.ROOT))) ci.cancel();
        }
    }

    private DisposableWorldMixin() {}
}
