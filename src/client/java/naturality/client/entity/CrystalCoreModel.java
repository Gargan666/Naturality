package naturality.client.entity;
import naturality.Naturality;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;

/** Separate inward-facing core pass: opaque emission without face-direction lighting. */
public final class CrystalCoreModel extends EndCrystalModel {
    public static final RenderType LAYER=RenderType.create("naturality_crystal_core",
        RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Naturality.id("pipeline/crystal_core"))
            .withShaderDefine("ALPHA_CUTOUT",.1F).withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING").withCull(true)
            .withColorTargetState(ColorTargetState.DEFAULT).build()))
            .withTexture("Sampler0",Naturality.id("textures/entity/end_crystal.png")).createRenderSetup());
    public CrystalCoreModel() {super(EndCrystalModel.createBodyLayer().bakeRoot());}
    public void setupAnim(EndCrystalRenderState state) {
        super.setupAnim(state);base.visible=false;
        outerGlass.skipDraw=true;innerGlass.skipDraw=true;cube.visible=true;
    }
}
