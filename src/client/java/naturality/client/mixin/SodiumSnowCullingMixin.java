package naturality.client.mixin;

import naturality.snow.SnowGeometry;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium caches state-only snow occlusion, which cannot describe a displaced mesh. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext",remap=false)
public abstract class SodiumSnowCullingMixin {
    @SuppressWarnings("null") @Shadow protected BlockAndTintGetter level;
    @SuppressWarnings("null") @Shadow protected BlockPos pos;

    @Inject(method="shouldDrawSide",at=@At("HEAD"),cancellable=true)
    private void naturality$displacedSnowFace(Direction facing,CallbackInfoReturnable<Boolean> cir) {
        if(!naturality.config.GameplaySettings.clientSnowWrapping())return;
        var neighbor=pos.relative(facing);
        if(level.getBlockState(neighbor).is(Blocks.SNOW) && !SnowGeometry.usesVanillaGeometry(level,neighbor))
            cir.setReturnValue(true);
    }
}
