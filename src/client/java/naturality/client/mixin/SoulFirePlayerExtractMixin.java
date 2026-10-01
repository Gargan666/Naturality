package naturality.client.mixin;

import naturality.client.fire.SoulFirePlayerState;
import naturality.client.fire.SoulFireTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class SoulFirePlayerExtractMixin {
    @Inject(method = "extractPlayerState", at = @At("TAIL"))
    private void naturality$markOverlay(Camera camera, DeltaTracker deltaTracker, float partialTick,
            PlayerRenderState state, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && !player.fireImmune() && player.isInLava()) state.isOnFire = true;
        ((SoulFirePlayerState) state).naturality$soulFireOverlay(
            player != null && state.isOnFire && SoulFireTracker.isSoulBurning(player));
    }
}
