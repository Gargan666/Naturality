package naturality.client.mixin;

import java.util.List;
import naturality.client.menu.MenuPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An external orbit cannot seed the native visibility walk from an unloaded camera chunk. */
@Mixin(SectionOcclusionGraph.class)
public abstract class MenuPreviewVisibilityMixin {
    @Shadow private @org.jspecify.annotations.Nullable ViewArea viewArea;
    @Inject(method = "addSectionsInFrustum", at = @At("HEAD"), cancellable = true)
    private void naturality$surfaceRegion(Frustum frustum, List<SectionRenderDispatcher.RenderSection> visible,
                                         List<SectionRenderDispatcher.RenderSection> nearby, CallbackInfo ci) {
        var area = viewArea;
        if (!MenuPreview.active() || area == null) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var spawn = MenuPreview.spawn();
        int cx = spawn.getX() >> 4, cz = spawn.getZ() >> 4;
        for (int z = cz - 7; z <= cz + 7; z++) for (int x = cx - 7; x <= cx + 7; x++) {
            if (!MenuPreview.containsChunk(x, z) || !level.getChunkSource().hasChunk(x, z)) continue;
            var chunk = level.getChunk(x, z);
            for (int i = 0; i < chunk.getSections().length; i++) {
                if (chunk.getSection(i).hasOnlyAir()) continue;
                int y = chunk.getSectionYFromSectionIndex(i);
                var section = area.getRenderSectionAt(new BlockPos(x << 4, y << 4, z << 4));
                // The fog skirt extends far outside the original section bounds.
                // Submit this bounded surface footprint and let GPU clipping handle
                // it, so an offscreen wall top cannot cull the visible wall below.
                if (section != null) visible.add(section);
            }
        }
        ci.cancel();
    }
}
