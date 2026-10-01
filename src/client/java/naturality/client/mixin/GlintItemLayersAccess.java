package naturality.client.mixin;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ItemStackRenderState.class)
public interface GlintItemLayersAccess {
    @Accessor("activeLayerCount") int naturality$activeLayers();
    @Accessor("layers") ItemStackRenderState.LayerRenderState[] naturality$layers();
}
