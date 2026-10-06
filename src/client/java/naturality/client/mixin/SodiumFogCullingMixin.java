package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.client.fog.FogCulling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Filters draw commands, including translucent draws and disabled face culling. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumFogCullingMixin {
    @ModifyExpressionValue(method = "fillCommandBuffer", at = @At(value = "INVOKE",
        target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getSliceMask(J)I"))
    private static int naturality$fogMask(int mask, @Local(index = 15) int x,
            @Local(index = 16) int y, @Local(index = 17) int z) {
        int drop = naturality.config.GameplaySettings.clientSnowWrapping()
            ? naturality.client.snow.SnowSectionVisibility.MAX_DROP : 0;
        return FogCulling.active() && FogCulling.sectionHidden(x << 4, y << 4, z << 4, drop) ? 0 : mask;
    }
}
