package naturality.client.mixin;

import naturality.weather.PreviewEnvironment;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(MinecraftServer.class)
public abstract class MenuPreviewInitialChunksMixin {
    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void naturality$cancelInitialChunks(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var server = (MinecraftServer)(Object)this;
        if (PreviewEnvironment.preview(server) && !server.isRunning()) ci.cancel();
    }
}
