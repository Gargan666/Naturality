package naturality.client.mixin;
import naturality.client.portal.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Hud.class)
public abstract class PortalOverlayMixin {
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="extractCameraOverlays",
        at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;lerp(FFF)F",ordinal=1))
    private float naturality$coverNearPlane(float alpha) {
        return naturality.config.GameplaySettings.clientPhysicalPortalEntry() ? PortalOverlay.cameraOpacity(alpha) : alpha;
    }
    @Inject(method="extractPortalOverlay",at=@At("HEAD"),cancellable=true)
    private void naturality$overlay(GuiGraphicsExtractor graphics,float alpha,CallbackInfo ci){
        if (!naturality.config.GameplaySettings.clientPhysicalPortalEntry()) return;
        var client=Minecraft.getInstance();
        int range=PortalTextureBrightness.packedRange(client);
        float min=(range&65535)/32767F,max=(range>>>16)/32767F;
        var sprite=client.getModelManager().getBlockStateModelSet().getParticleMaterial(Blocks.NETHER_PORTAL.defaultBlockState()).sprite();
        graphics.blitSprite(PortalOverlay.PIPELINE,sprite,0,0,graphics.guiWidth(),graphics.guiHeight(),ARGB.colorFromFloat(1,alpha,min,max));
        ci.cancel();
    }
}
