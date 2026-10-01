package naturality.client.mixin;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carry emission into the terrain material, not just CPU quad lighting. */
@Mixin(BakedQuad.MaterialInfo.class)
public abstract class PortalMaterialEmissiveMixin {
    @Inject(method = "of", at = @At("RETURN"), cancellable = true)
    private static void naturality$emissivePortalMaterial(CallbackInfoReturnable<BakedQuad.MaterialInfo> ci) {
        BakedQuad.MaterialInfo material = ci.getReturnValue();
        if (naturality.config.NaturalityConfig.get().portalChanges.portalBlockChanges && material.sprite().contents().name().equals(Identifier.withDefaultNamespace("block/nether_portal"))) {
            ci.setReturnValue(new BakedQuad.MaterialInfo(material.sprite(), material.layer(),
                material.itemRenderType(), material.itemGlintRenderType(), material.itemGlintSpecialRenderType(),
                material.tintIndex(), Direction.UP, 15));
        }
    }
}
