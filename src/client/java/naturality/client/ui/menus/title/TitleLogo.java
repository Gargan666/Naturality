package naturality.client.ui.menus.title;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

@org.jspecify.annotations.NullMarked
public final class TitleLogo {
    private TitleLogo() {}
    private static final Identifier NATURALITY_LOGO =
            Identifier.fromNamespaceAndPath("naturality", "textures/gui/title/minecraft.png");


    public static void logo(LogoRenderer renderer, GuiGraphicsExtractor graphics, int width, float alpha) {
        // Match the reference image: 60% of the viewport, independent of GUI scale.
        int logoWidth = Math.max(1, Math.round(width * 0.6F));
        int logoHeight = Math.max(1, Math.round(logoWidth * (43.0F / 271.0F)));
        // Sample the entire artist image instead of vanilla's cropped UV region.
        graphics.blit(RenderPipelines.GUI_TEXTURED, NATURALITY_LOGO, (width - logoWidth) / 2,
                Math.round(graphics.guiHeight() * 0.09F), 0.0F, 0.0F, logoWidth, logoHeight,
                271, 43, 271, 43,
                ARGB.white(renderer.keepLogoThroughFade() ? 1.0F : alpha));
    }

    public static void splash(GuiGraphicsExtractor graphics, int width, Runnable draw) {
        float scale = width / 640.0F;
        float logoBottom = graphics.guiHeight() * 0.09F + width * 0.6F * (43.0F / 271.0F);
        float splashX = width * 0.72F;
        float splashY = logoBottom + graphics.guiHeight() * 0.025F;
        graphics.pose().pushMatrix();
        try {
            // Offset the native anchor while retaining its angle, pulse and fade.
            graphics.pose().translate(splashX - 123.0F * scale, splashY - 69.0F * scale);
            graphics.pose().scale(scale, scale);
            draw.run();
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
