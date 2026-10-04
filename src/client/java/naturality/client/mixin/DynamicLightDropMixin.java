package naturality.client.mixin;

import naturality.client.lighting.DynamicLighting;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class DynamicLightDropMixin {
    @Inject(method = "dropItem", at = @At("HEAD"))
    private void naturality$bridgeDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
        DynamicLighting.beginDrop(player, player.getMainHandItem());
    }
}
