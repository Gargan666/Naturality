package naturality.client.particles.sand.mixins;

import naturality.client.particles.sand.SandPlacementBurst;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelEventHandler.class)
@org.jspecify.annotations.NullMarked
public class SandPlacementEventMixin {
    @SuppressWarnings("null") @Shadow @Final private ClientLevel level;

    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
    private void naturality$placementBurst(int event, BlockPos pos, int data, CallbackInfo ci) {
        if (event == 0x4E5301) {
            SandPlacementBurst.spawn(level, pos, Block.stateById(data));
            ci.cancel();
        }
    }
}
