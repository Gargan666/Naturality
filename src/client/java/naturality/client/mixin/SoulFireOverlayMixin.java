package naturality.client.mixin;

import naturality.client.fire.SoulFirePlayerState;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ScreenEffectRenderer.class)
public abstract class SoulFireOverlayMixin {
    @Redirect(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/sprite/SpriteGetter;get(Lnet/minecraft/client/resources/model/sprite/SpriteId;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite naturality$soulFireOverlay(SpriteGetter sprites, SpriteId id, float partialTicks,
            SubmitNodeCollector collector, PlayerRenderState playerState, CameraRenderState cameraState, boolean hideGui) {
        if (id == ModelBakery.FIRE_1 && ((SoulFirePlayerState) playerState).naturality$soulFireOverlay()) {
            id = new SpriteId(naturality.client.AtlasLocations.BLOCKS, Identifier.withDefaultNamespace("block/soul_fire_1"));
        }
        return sprites.get(id);
    }
}
