package naturality.client.mixin;

import naturality.client.lighting.DynamicLighting;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightCoordsUtil.class)
public abstract class DynamicLightCoordsMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
        at = @At("RETURN"), cancellable = true)
    private static void naturality$fractionalLight(LightCoordsUtil.BrightnessGetter brightnessGetter,
            BlockAndLightGetter level, BlockState block, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (DynamicLighting.sourceCount() == 0 || cir.getReturnValueI() == LightCoordsUtil.FULL_BRIGHT) return;
        // Preview worlds and other mods may implement brightness without a real
        // light engine. Only touch the client world and its terrain snapshots.
        if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel)
                && !(level instanceof net.minecraft.client.renderer.chunk.RenderSectionRegion)) return;
        int extra = DynamicLighting.smoothLight(level.getLightEngine().getLayerListener(LightLayer.BLOCK), pos.asLong());
        if (extra > LightCoordsUtil.smoothBlock(cir.getReturnValueI()))
            cir.setReturnValue(LightCoordsUtil.smoothPack(extra, LightCoordsUtil.smoothSky(cir.getReturnValueI())));
    }
}
