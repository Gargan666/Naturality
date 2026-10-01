package naturality.client.mixin;

import java.util.HashMap;
import naturality.client.snow.SnowRenderRegion;
import naturality.config.GameplaySettings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderRegionCache.class)
public abstract class SnowRenderRegionCacheMixin {
    @Invoker("getSectionDataCopy")
    protected abstract SectionCopy naturality$copy(Level level, int x, int y, int z);

    @Inject(method = "createRegion", at = @At("RETURN"))
    private void naturality$captureSnow(ClientLevel level, long node, CallbackInfoReturnable<RenderSectionRegion> cir) {
        if (!GameplaySettings.clientSnowWrapping()) return;
        int x = SectionPos.x(node), y = SectionPos.y(node), z = SectionPos.z(node);
        var copies = new HashMap<Long, SectionCopy>();
        // A 32-block owner/support search plus one AO neighbor needs a three-section
        // vertical margin. Reuse vanilla's cache and capture before workers start.
        for (int dy : new int[] {-3, -2, 2, 3}) {
            if (y + dy < level.getMinSectionY() || y + dy >= level.getMaxSectionY()) continue;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                copies.put(SectionPos.asLong(x + dx, y + dy, z + dz),
                    naturality$copy(level, x + dx, y + dy, z + dz));
            }
        }
        ((SnowRenderRegion) cir.getReturnValue()).naturality$setSnowSections(copies);
    }
}
