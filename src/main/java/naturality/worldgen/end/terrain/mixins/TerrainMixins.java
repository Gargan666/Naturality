package naturality.worldgen.end.terrain.mixins;

import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.dimension.end.DragonRespawnStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import naturality.worldgen.end.terrain.EndTerrainDensity.EndIslandMaskCache;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.EndIslandFunction;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.worldgen.end.terrain.EndTerrainDensity;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class TerrainMixins {
    private TerrainMixins() {}

    @Mixin(EnderDragonFight.class)
    @org.jspecify.annotations.NullMarked
    public interface EndDragonFightAccess {
        @Accessor("dragonKilled") boolean naturality$dragonKilled();
        @Accessor("respawnStage") DragonRespawnStage naturality$respawnStage();
    }

    @Mixin(EndIslandFunction.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndIslandMaskCacheMixin {
        @Inject(method = "compileSampler", at = @At("RETURN"), cancellable = true)
        private void naturality$cacheIslandMask(DensityFunction.CompileContext context, CallbackInfoReturnable<DensitySampler> cir) {
            cir.setReturnValue(new EndIslandMaskCache(cir.getReturnValue()));
        }
    }

    /** Vanilla eagerly saves full chunks, but leaves the generation halo until unload. */
    @Mixin(ChunkMap.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndProtoChunkSaveMixin {
        @SuppressWarnings("null") @Shadow @Final private ServerLevel level;
        @SuppressWarnings("null") @Shadow private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;
        @SuppressWarnings("null") @Shadow @Final private AtomicInteger activeChunkWrites;
        @SuppressWarnings("null") @Shadow @Final private Long2LongMap nextChunkSaveTime;
        @Shadow private boolean save(ChunkAccess chunk) { throw new AssertionError(); }
        @Unique private Iterator<ChunkHolder> naturality$saveCandidates = java.util.Collections.emptyIterator();

        @Inject(method = "acquireGeneration", at = @At("RETURN"))
        private void naturality$resumeEndGeneration(long key, CallbackInfoReturnable<GenerationChunkHolder> cir) {
            if (!level.dimension().equals(Level.END)) return;
            var chunk = cir.getReturnValue().getLatestChunk();
            // ProtoChunk block/entity writes assume the generation stage is already
            // dirty. A neighbor's decoration can change it without advancing its own
            // status, so reusing a checkpoint for generation must restore that invariant.
            if (chunk instanceof ProtoChunk && !(chunk instanceof ImposterProtoChunk)) chunk.markUnsaved();
        }

        @Inject(method = "saveChunksEagerly", at = @At("TAIL"))
        private void naturality$checkpointEndHalo(BooleanSupplier haveTime, CallbackInfo ci) {
            var server = level.getServer();
            if (!level.dimension().equals(Level.END) || level.noSave() || server == null || !server.isRunning()
                    || activeChunkWrites.get() >= 16 || !haveTime.getAsBoolean()) return;
            // visibleChunkMap is a published snapshot: retain its iterator across ticks,
            // then reject holders no longer present. No full-map allocation or chunk loads.
            if (!naturality$saveCandidates.hasNext()) naturality$saveCandidates = visibleChunkMap.values().iterator();
            long deadline = System.nanoTime() + 1_000_000L;
            long now = Util.getMillis();
            int saved = 0;
            for (int checked = 0; checked < 64 && saved < 2 && naturality$saveCandidates.hasNext()
                    && activeChunkWrites.get() < 16 && haveTime.getAsBoolean() && System.nanoTime() < deadline; checked++) {
                var holder = naturality$saveCandidates.next();
                long key = holder.getPos().pack();
                if (visibleChunkMap.get(key) != holder || !holder.isReadyForSaving()
                        || now < nextChunkSaveTime.getOrDefault(key, -1L)) continue;
                var chunk = holder.getLatestChunk();
                if (!(chunk instanceof ProtoChunk) || chunk instanceof ImposterProtoChunk || !chunk.isUnsaved()
                        || chunk.getPersistedStatus() == ChunkStatus.EMPTY) continue;
                // Use vanilla's snapshot, encoding, existing-full-chunk guard and I/O
                // failure handling. Normal flush/close still waits for every write.
                if (save(chunk)) {
                    nextChunkSaveTime.put(key, now + 30_000L);
                    saved++;
                }
            }
        }
    }

    @Mixin(NoiseBasedChunkGenerator.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndTerrainDensityMixin {
        @WrapOperation(method = {"doFill", "iterateNoiseColumn"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;"))
        private DensityFunction naturality$outerTerrain(NoiseRouter router, Operation<DensityFunction> original) {
            DensityFunction density = original.call(router);
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.END)
                ? new EndTerrainDensity(density) : density;
        }
    }
}
