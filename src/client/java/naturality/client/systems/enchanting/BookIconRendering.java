package naturality.client.systems.enchanting;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import naturality.Naturality;
import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Matrix3x2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Unlit icon overlays; deliberately independent of the book's mesh, atlas and foil layers. */
@org.jspecify.annotations.NullMarked
public final class BookIconRendering {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Naturality.id("pipeline/book_icon"))
            .withShaderDefine("ALPHA_CUTOUT", .1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withCull(false)
            // Opaque icon texels must occlude later clouds and the depth-tested glint contour pass.
            // Alpha-cutout fragments are discarded before depth writes, preserving the sprite silhouette.
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(ColorTargetState.DEFAULT).build());
    private static final java.util.Map<Identifier, RenderType> LAYERS = new java.util.HashMap<>();
    private static @Nullable List<GuiIcon> hudIcons;

    private record GuiIcon(Identifier texture, int x, int y, Matrix3x2f pose) {}

    /** Preserve slot transforms, but submit HUD icons after the survival bars. */
    public static void hud(GuiGraphicsExtractor graphics, Runnable extract) {
        var previous = hudIcons;
        var icons = new ArrayList<GuiIcon>();
        hudIcons = icons;
        try {
            extract.run();
        } finally {
            hudIcons = previous;
        }
        if (previous != null) {
            previous.addAll(icons);
        } else if (!icons.isEmpty()) {
            graphics.nextStratum();
            for (var icon : icons) {
                graphics.pose().pushMatrix();
                graphics.pose().set(icon.pose());
                drawInventory(graphics, icon.texture(), icon.x(), icon.y());
                graphics.pose().popMatrix();
            }
        }
    }

    private static RenderType layer(Identifier texture) {
        return LAYERS.computeIfAbsent(texture, id -> RenderType.create("naturality_book_icon",
            RenderSetup.builder(PIPELINE)
                .withTexture("Sampler0", id, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                .createRenderSetup()));
    }

    private BookIconRendering() {}

    public interface IconState {
        void naturality$bookIcon(boolean visible, Identifier texture, ItemDisplayContext context);
    }

    public static boolean visible(ItemStack item) {
        return item.is(Items.ENCHANTED_BOOK) && item.getOrDefault(Enchanting.REVEALED, false);
    }

    private static float wave() {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null) return 0;
        double ticks = level.getGameTime() % 80 + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return (float)Math.sin(ticks * Math.PI / 40);
    }

    public static void inventory(GuiGraphicsExtractor graphics, ItemStack item, int x, int y) {
        if (!visible(item)) return;
        var texture = EnchantmentIcons.book(item);
        var icons = hudIcons;
        if (icons != null) {
            icons.add(new GuiIcon(texture, x, y, new Matrix3x2f(graphics.pose())));
            return;
        }
        drawInventory(graphics, texture, x, y);
    }

    private static void drawInventory(GuiGraphicsExtractor graphics, Identifier texture, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, wave() * 1.25F);
        // Centre the smaller icon above the book, allowing it to float beyond the slot.
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + 2, y - 6, 0, 0, 12, 12, 16, 16, 16, 16);
        graphics.pose().popMatrix();
    }

    public static void submit(ItemStackRenderState item, Identifier texture, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector) {
        if (context == ItemDisplayContext.GUI || item.isEmpty()) return;
        var camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (!camera.initialized) return;
        var view = new Matrix4f(camera.viewRotationMatrix);
        var itemToView = new Matrix4f(view).mul(pose.last().pose());
        var bounds = item.getModelBoundingBox();
        var center = new Vector3f((float)((bounds.minX + bounds.maxX) * .5),
            (float)((bounds.minY + bounds.maxY) * .5), (float)((bounds.minZ + bounds.maxZ) * .5));
        var worldCenter = pose.last().pose().transformPosition(center, new Vector3f());
        float worldTop = -Float.MAX_VALUE;
        float nearestDepth = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            var point = new Vector3f((float)((corner & 1) == 0 ? bounds.minX : bounds.maxX),
                (float)((corner & 2) == 0 ? bounds.minY : bounds.maxY),
                (float)((corner & 4) == 0 ? bounds.minZ : bounds.maxZ));
            worldTop = Math.max(worldTop, pose.last().pose().transformPosition(point, new Vector3f()).y);
            nearestDepth = Math.max(nearestDepth, itemToView.transformPosition(point, new Vector3f()).z);
        }
        float scale = pose.last().pose().getScale(new Vector3f()).length() / (float)Math.sqrt(3);
        float bob = wave() * .035F * scale;
        boolean dropped = context == ItemDisplayContext.GROUND;
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean framed = context == ItemDisplayContext.FIXED;
        if (dropped) {
            // World-up anchor: no camera-facing positional offset for dropped books.
            worldCenter.y = worldTop + .19F * scale + bob;
            view.transformPosition(worldCenter, center);
        } else {
            view.transformPosition(worldCenter, center);
            // First-person items sit close to the projection plane: moving the anchor
            // forward magnifies it and pushes it off-screen. Depth is handled below.
            float inward = firstPerson
                ? (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? -.12F : .12F) * scale : 0;
            center.add(inward, (firstPerson ? .18F : framed ? .45F : .25F) * scale + bob, 0);
        }
        if (center.z >= -.06F) return;
        float size = (dropped ? .30F : firstPerson ? .22F : framed ? .80F : .40F) * scale;
        // Bring depth just ahead of the book while preserving every projected pixel.
        // Unlike disabling depth tests, nearer world geometry can still occlude the icon.
        float front = Math.min(-.06F, Math.max(center.z, nearestDepth + .015F * scale));
        float perspectiveScale = front / center.z;
        center.mul(perspectiveScale);
        float half = size * perspectiveScale * .5F;
        var billboard = new PoseStack();
        billboard.mulPose(view.invert());
        billboard.translate(center.x, center.y, center.z);
        collector.order(1).submitCustomGeometry(billboard, layer(texture), (submitted, buffer) -> {
            buffer.addVertex(submitted, -half, -half, 0).setColor(-1).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(submitted, 0, 0, 1);
            buffer.addVertex(submitted, half, -half, 0).setColor(-1).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(submitted, 0, 0, 1);
            buffer.addVertex(submitted, half, half, 0).setColor(-1).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(submitted, 0, 0, 1);
            buffer.addVertex(submitted, -half, half, 0).setColor(-1).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(submitted, 0, 0, 1);
        });
    }
}
