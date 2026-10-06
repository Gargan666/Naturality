package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.snow.SnowSectionVisibility;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SectionCompiler.class)
public abstract class SnowSectionCompilerMixin {
    @WrapMethod(method="compile")
    private SectionCompiler.Results naturality$captureSnowBounds(SectionPos section,
            RenderSectionRegion region, VertexSorting sorting, SectionBufferBuilderPack builders,
            Operation<SectionCompiler.Results> original) {
        SnowSectionVisibility.begin(section);
        naturality.snow.SnowGeometryCache.begin();
        try { return original.call(section, region, sorting, builders); }
        finally { naturality.snow.SnowGeometryCache.end(); SnowSectionVisibility.finish(); }
    }
}
