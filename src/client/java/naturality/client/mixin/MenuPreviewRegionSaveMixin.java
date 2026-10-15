package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also covers POI/entity writes queued during native close or unload. */
@Mixin(IOWorker.class)
public abstract class MenuPreviewRegionSaveMixin {
    @Unique private boolean naturality$discard;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void naturality$identify(RegionStorageInfo info, Path directory, boolean sync, CallbackInfo ci) {
        naturality$discard = MenuPreview.temporaryPath(directory);
    }
    @Inject(method = "store(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"), cancellable = true)
    private void naturality$discardRegion(ChunkPos pos, Supplier<CompoundTag> data, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (naturality$discard) cir.setReturnValue(CompletableFuture.completedFuture(null));
    }
}
