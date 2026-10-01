package naturality.client.mixin;
import naturality.client.glint.InventoryGlintContours;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiRenderer.class)
public abstract class InventoryGlintContourMixin {
    @SuppressWarnings("null") @Shadow @Final private GuiRenderState renderState;
    @Inject(method = "submitBlitFromItemAtlas", at = @At("TAIL"))
    private void naturality$outline(GuiItemRenderState item, GuiItemAtlas.SlotView slot, CallbackInfo ci) {
        InventoryGlintContours.submit(renderState, item, slot);
    }
}
