package naturality.client.glint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import naturality.Naturality;
import naturality.client.mixin.GlintItemLayersAccess;
import naturality.client.mixin.GlintLayerFoilAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.ARGB;

/** Outline the final inventory sprite alpha, with atlas-slot isolation. */
public final class InventoryGlintContours {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Naturality.id("pipeline/inventory_glint_contour"))
            .withVertexShader(Naturality.id("core/inventory_glint_contour"))
            .withFragmentShader(Naturality.id("core/inventory_glint_contour"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)).build());

    public static void submit(GuiRenderState gui, GuiItemRenderState item, GuiItemAtlas.SlotView slot) {
        if (!naturality.config.NaturalityConfig.get().effects.inventoryGlintContours) return;
        var access = (GlintItemLayersAccess) item.itemStackRenderState();
        boolean enchanted = false;
        for (int i = 0; i < access.naturality$activeLayers(); i++) {
            if (((GlintLayerFoilAccess) access.naturality$layers()[i]).naturality$foil() != ItemStackRenderState.FoilType.NONE) {
                enchanted = true; break;
            }
        }
        if (!enchanted) return;
        int width = slot.textureView().getWidth(0), height = slot.textureView().getHeight(0);
        int scale = Math.max(1, Math.round((slot.u1() - slot.u0()) * width / 16));
        int slotX = Math.round(slot.u0() * width / (16 * scale));
        // Slots are allocated from the top; the atlas height need not divide by slot size.
        int slotY = Math.round((1.0f - slot.v0()) * height / (16 * scale));
        // Metadata occupies flat vertex color; it is never used as visible tint.
        if (slotX > 255 || slotY > 255 || scale > 255) return;
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var palette = Minecraft.getInstance().getTextureManager().getTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
        float du = (slot.u1() - slot.u0()) / 8, dv = (slot.v0() - slot.v1()) / 8;
        gui.addBlitToCurrentLayer(new BlitRenderState(PIPELINE,
            TextureSetup.doubleTexture(slot.textureView(), sampler, palette.getTextureView(), sampler),
            item.pose(), item.x() - 2, item.y() - 2, item.x() + 18, item.y() + 18,
            slot.u0() - du, slot.u1() + du, slot.v0() + dv, slot.v1() - dv,
            ARGB.color(255, slotX, slotY, scale), item.scissorArea(), null));
    }
    private InventoryGlintContours() { }
}



