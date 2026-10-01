package naturality.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import naturality.client.fluid.ProceduralFluids;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSectionsToRender.class)
public abstract class FluidTerrainMixin {
    @Inject(method = "renderLayers", at = @At("HEAD"))
    private void naturality$bindFluids(ChunkSectionLayer[] layers, GpuSampler sampler, RenderPass pass,
            GpuTextureView atlas, GpuTextureView lightmap, RenderPipeline override,
            RenderPipeline multidrawOverride, CallbackInfo ci) {
        ProceduralFluids.bind(pass);
        naturality.client.weather.WindRendering.bind(pass);
        naturality.client.fire.ProceduralFire.bind(pass);
        naturality.client.fluid.WaterVisuals.bind(pass);
        naturality.client.fluid.WaterVisuals.bindScene(pass);
    }
}
