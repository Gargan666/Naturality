package naturality.client.weather;
import naturality.Naturality;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.*;
public final class LightningRendering {
    private static final RenderPipeline.Snippet SHADERS=RenderPipeline.builder()
        .withVertexShader(Naturality.id("core/weather_lightning")).withFragmentShader(Naturality.id("core/weather_lightning")).buildSnippet();
    private static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET,SHADERS)
        .withLocation(Naturality.id("pipeline/lightning")).withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).build());
    private static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_lightning",
        RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET,SHADERS).withShaderDefine("OIT_ADDITIVE")).build());
    public static final RenderType TYPE=RenderType.create("naturality_lightning",RenderSetup.builder(PIPELINE).setOitPipelines(OIT).sortOnUpload().createRenderSetup());
    private LightningRendering() {}
}
