package naturality.client.mixin;

import java.util.ArrayList;
import java.util.List;
import naturality.client.snow.SnowTextureAtlas;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SpriteLoader.class)
public abstract class SnowAtlasSpaceMixin {
    @SuppressWarnings("null") @Shadow @Final private Identifier location;
    @Shadow @Final private int maxSupportedTextureSize;
    @ModifyVariable(method="stitch",at=@At("HEAD"),argsOnly=true)
    private List<SpriteContents> naturality$compositeSpace(List<SpriteContents> original) {
        if(!location.equals(naturality.client.AtlasLocations.BLOCKS))return original;
        var result=new ArrayList<>(original);
        result.add(SnowTextureAtlas.reserve(maxSupportedTextureSize));
        return result;
    }
}
