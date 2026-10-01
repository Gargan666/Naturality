package naturality.client.mixin;

import naturality.client.portal.end.EndEyeGlow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.LevelEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelEventHandler.class)
public class EndEyeInsertionMixin {
    @SuppressWarnings("null") @Shadow @Final private ClientLevel level;

    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
    private void naturality$eyeGlow(int eventType, BlockPos pos, int data, CallbackInfo ci) {
        if (!naturality.config.NaturalityConfig.get().portalChanges.eyeGlow || eventType != LevelEvent.END_PORTAL_FRAME_FILL) return;
        EndEyeGlow.start(level, pos);
        level.playLocalSound(pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1, 1, false);
        // Replace the insertion puff as well; retain vanilla positional audio.
        ci.cancel();
    }
}
