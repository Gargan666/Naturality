package naturality.client.mixin;

import naturality.weather.PreviewEnvironment;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class MenuPreviewChunkSaveMixin {
    @SuppressWarnings("null") @Shadow @Final private ServerLevel level;
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void naturality$discardChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (PreviewEnvironment.preview(level.getServer())) { cir.setReturnValue(false); }
    }
}
