package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import naturality.client.fog.FogCulling;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class TerrainFogCullingMixin {
    @Unique private final ObjectArrayList<SectionRenderDispatcher.RenderSection> naturality$fogSections = new ObjectArrayList<>();

    @ModifyExpressionValue(method = "extractSectionDrawGroups", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> naturality$visibleInFog(
            ObjectArrayList<SectionRenderDispatcher.RenderSection> original) {
        naturality$fogSections.clear();
        if (!FogCulling.active()) return original;
        for (var section : original) {
            var pos = section.getRenderOrigin();
            if (!FogCulling.sectionHidden(pos.getX(), pos.getY(), pos.getZ())) naturality$fogSections.add(section);
        }
        return naturality$fogSections;
    }
}
