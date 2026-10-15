package naturality.client.mixin;

import naturality.weather.PreviewEnvironment;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class MenuPreviewPlayerSaveMixin {
    @SuppressWarnings("null") @Shadow @Final private MinecraftServer server;
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void naturality$discardPlayer(ServerPlayer player, CallbackInfo ci) {
        if (PreviewEnvironment.preview(server)) ci.cancel();
    }
}
