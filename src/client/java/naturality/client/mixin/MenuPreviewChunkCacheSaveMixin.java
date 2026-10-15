package naturality.client.mixin;

import naturality.weather.PreviewEnvironment;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class MenuPreviewChunkCacheSaveMixin {
    @SuppressWarnings("null") @Shadow @Final private ServerLevel level;
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void naturality$discardFlush(boolean flush, CallbackInfo ci) {
        if (PreviewEnvironment.preview(level.getServer())) ci.cancel();
    }
}
