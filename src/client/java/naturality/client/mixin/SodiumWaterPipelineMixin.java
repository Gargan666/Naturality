package naturality.client.mixin;

import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import naturality.Naturality;
import naturality.client.fluid.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap=false)
public abstract class SodiumWaterPipelineMixin {
    @Shadow @Final protected VertexFormat vertexFormat;
    @Unique private RenderPipeline naturality$mask;

    @ModifyExpressionValue(method={"createShader", "createOITShader"}, at=@At(value="INVOKE",
        target="Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withFragmentShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
    private RenderPipeline.Builder naturality$waterShaders(RenderPipeline.Builder builder) {
        return naturality$shaders(builder);
    }

    @Unique private static RenderPipeline.Builder naturality$shaders(RenderPipeline.Builder builder) {
        return builder.withVertexShader(Naturality.id("core/sodium_water"))
            .withFragmentShader("core/terrain").withShaderDefine("NATURALITY_SODIUM")
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.FOG)
            .withBindGroupLayout(ProceduralFluids.LAYOUT)
            .withBindGroupLayout(naturality.client.fire.ProceduralFire.SODIUM_LAYOUT)
            .withBindGroupLayout(naturality.client.weather.WindRendering.LAYOUT)
            .withBindGroupLayout(WaterVisuals.LAYOUT)
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform("NaturalityWaterAtmosphere", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("NaturalityWaterOpaqueDepth", UniformType.COMBINED_IMAGE_SAMPLER).build());
    }

    @Inject(method="compileProgram", at=@At("HEAD"), cancellable=true)
    private void naturality$maskPipeline(CallbackInfoReturnable<RenderPipeline> ci) {
        if (!WaterComposite.drawingMask()) return;
        if (naturality$mask==null) {
            var globals=BindGroupLayout.builder()
                .withUniform("u_BlockTex", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("u_Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("u_SectionTimeInfo", UniformType.TEXEL_BUFFER, GpuFormat.R32_SINT).build();
            var light=BindGroupLayout.builder().withUniform("u_LightTex", UniformType.COMBINED_IMAGE_SAMPLER).build();
            naturality$mask=naturality$shaders(RenderPipeline.builder().withLocation(Naturality.id("pipeline/sodium_water_mask"))
                .withBindGroupLayout(globals).withBindGroupLayout(light).withPushConstantSize(20)
                .withVertexBinding(0,vertexFormat).withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(true).withShaderDefine("USE_VERTEX_COMPRESSION").withShaderDefine("NATURALITY_WATER_MASK")
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true))
                .withColorTargetState(new ColorTargetState(java.util.Optional.empty(),GpuFormat.RGBA32_FLOAT,15))).build();
        }
        ci.setReturnValue(naturality$mask);
    }
}
