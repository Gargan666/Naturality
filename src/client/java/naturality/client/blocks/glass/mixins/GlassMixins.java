package naturality.client.blocks.glass.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import naturality.client.blocks.glass.GlassShine;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import naturality.client.rendering.distantdetail.DistantTextures;

/** Independent glass material hook; the outer container is inert. */
public final class GlassMixins {
    private GlassMixins() {}

    @Mixin(BakedQuad.MaterialInfo.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class GlassMaterialMixin {
        @ModifyReturnValue(method = "of", at = @At("RETURN"))
        private static BakedQuad.MaterialInfo naturality$reflectiveGlass(BakedQuad.MaterialInfo material) {
            // Clear vanilla glass is cutout. Its empty texels need the translucent
            // pass to carry a faint reflection without writing opaque depth.
            if (!GlassShine.isGlass(material.sprite().contents().name())
                    || material.layer() == ChunkSectionLayer.TRANSLUCENT) return material;
            return new BakedQuad.MaterialInfo(material.sprite(), ChunkSectionLayer.TRANSLUCENT,
                material.itemRenderType(), material.itemGlintRenderType(), material.itemGlintSpecialRenderType(),
                material.tintIndex(), material.shadeDirectionOverride(), material.lightEmission());
        }
    }

    /** Fabric/custom models can emit their own cutout quads without MaterialInfo.of. */
    @Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
    @org.jspecify.annotations.NullMarked
    public static abstract class GlassTerrainMixin {
        @Inject(method = "transform", at = @At("HEAD"))
        private void naturality$glassLayer(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
            if (quad.chunkLayer() == ChunkSectionLayer.TRANSLUCENT) return;
            float u = 0, v = 0;
            for (int i = 0; i < 4; i++) { u += quad.u(i); v += quad.v(i); }
            var sprite = DistantTextures.find(u / 4, v / 4);
            if (sprite != null && GlassShine.isGlass(sprite.contents().name()))
                quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
        }
    }
}
