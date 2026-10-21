package naturality.client.systems.enchanting;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;

/** Stored item models above the native table book, with an orbiting material stack. */
@org.jspecify.annotations.NullMarked
public final class TableItemRendering {
    private TableItemRendering() {}

    public interface Access { Display naturality$display(); }

    public static final class Display {
        private final ItemStackRenderState item = new ItemStackRenderState();
        private final ItemStackRenderState lapis = new ItemStackRenderState();
        private boolean upright;
        private float ticks;
        private float closedBookLift;

        public void extract(EnchantingTableBlockEntity entity, float partialTick) {
            item.clear(); lapis.clear();
            var level = entity.getLevel();
            if (level == null || !(entity instanceof Enchanting.TableAccess access)) return;
            var table = access.naturality$enchanting();
            var stack = table.getItem(0);
            upright = stack.is(ItemTags.SWORDS) || stack.has(DataComponents.TOOL);
            ticks = (level.getGameTime() % 2400) + partialTick;
            float closed = 1f - net.minecraft.util.Mth.clamp(
                net.minecraft.util.Mth.lerp(partialTick, entity.oOpen, entity.open), 0f, 1f);
            // Follow the interpolated cover animation, easing to rest at either end.
            closedBookLift = .35f * closed * closed * (3f - 2f * closed);
            var resolver = Minecraft.getInstance().getItemModelResolver();
            int seed = entity.getBlockPos().hashCode();
            // NONE preserves the authored sprite orientation before the blade-up rotation.
            resolver.updateForTopItem(item, stack, ItemDisplayContext.NONE, level, null, seed);
            resolver.updateForTopItem(lapis, table.getItem(2), ItemDisplayContext.NONE, level, null, seed + 1);
        }

        public void submit(PoseStack pose, SubmitNodeCollector collector, int light) {
            double bob = Math.sin(ticks * Math.PI / 40) * .06;
            pose.pushPose();
            pose.translate(.5, 1.55 + closedBookLift + bob, .5);
            pose.rotateDegrees(Axis.YP, ticks * .6F);
            if (upright) pose.rotateDegrees(Axis.ZP, 45);
            pose.scale(.7F, .7F, .7F);
            item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
            double orbit = ticks * Math.PI / 120;
            pose.pushPose();
            pose.translate(.5 + Math.cos(orbit) * .48, 1.45 + closedBookLift + Math.sin(ticks * Math.PI / 40 + 1) * .06,
                .5 + Math.sin(orbit) * .48);
            pose.rotateDegrees(Axis.YP, ticks * .9F);
            pose.scale(.35F, .35F, .35F);
            lapis.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
