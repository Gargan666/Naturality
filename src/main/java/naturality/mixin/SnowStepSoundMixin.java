package naturality.mixin;

import naturality.snow.SnowGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SnowStepSoundMixin {
    @Shadow protected abstract void playStepSound(BlockPos pos, BlockState state);

    @Inject(method = "walkingStepSound", at = @At("HEAD"), cancellable = true)
    private void naturality$displacedSnowStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        Entity entity = (Entity)(Object)this;
        if (state.is(Blocks.SNOW) || !naturality.config.GameplaySettings.snowWrapping(entity.level())) return;
        if (SnowGeometry.coversFoot(entity.level(), pos, entity.getBoundingBox())) {
            this.playStepSound(pos, Blocks.SNOW.defaultBlockState());
            ci.cancel();
        }
    }
}
