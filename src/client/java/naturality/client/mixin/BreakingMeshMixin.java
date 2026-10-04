package naturality.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import naturality.client.breaking.BreakingTextures;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.ModelBakery;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Capture the selected texture before Fabric defers the mesh draw. */
@Mixin(ExtendedBlockModelSubmit.class)
public abstract class BreakingMeshMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Function<ChunkSectionLayer, @Nullable RenderType> naturality$texture(
            Function<ChunkSectionLayer, @Nullable RenderType> original,
            @Local(argsOnly = true, ordinal = 1) PoseStack.@Nullable Pose decal) {
        if (decal == null) return original;
        RenderType fallback = original.apply(ChunkSectionLayer.SOLID);
        if (fallback == null) return original;
        int stage = ModelBakery.DESTROY_TYPES.indexOf(fallback);
        boolean oit = stage < 0;
        if (oit) stage = ModelBakery.DESTROY_TYPES_OIT.indexOf(fallback);
        if (stage < 0) return original;
        RenderType texture = BreakingTextures.current(stage, oit, fallback);
        return _ -> texture;
    }
}
