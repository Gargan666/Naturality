package naturality.client.mixin;

import java.util.Map;
import naturality.client.fluid.ProceduralFluids;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class FluidAtlasMixin {
    @SuppressWarnings("null") @Shadow private Map<Identifier, TextureAtlasSprite> texturesByName;

    @Inject(method = "upload", at = @At("TAIL"))
    private void naturality$fluidBounds(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (((TextureAtlas) (Object) this).location().equals(naturality.client.AtlasLocations.BLOCKS)) {
            naturality.client.portal.FlatModelAlpha.clear();
            naturality.client.snow.SnowOverlayModel.atlasLoaded(texturesByName);
            ProceduralFluids.atlasLoaded(texturesByName);
            naturality.client.fire.ProceduralFire.atlasLoaded(texturesByName);
        }
    }
}


