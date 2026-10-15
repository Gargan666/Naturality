package naturality.client.entities.maddened;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.util.Mth;

/** Exact geometry and 64x64 UV layout from the supplied Blockbench export. */
public final class MaddenedCreeperModel extends EntityModel<CreeperRenderState> {
    private final ModelPart head;
    private final ModelPart[] legs = new ModelPart[4];
    public MaddenedCreeperModel() {
        super(createBodyLayer().bakeRoot());
        var body = root().getChild("Body");
        head = body.getChild("Head");
        for (int i = 0; i < 4; i++) legs[i] = body.getChild("leg" + i);
    }
    public static LayerDefinition createBodyLayer() {
        return MaddenedCreeperGeometry.createBodyLayer();
    }

    @Override public void setupAnim(CreeperRenderState state) {
        super.setupAnim(state);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        for (int i = 0; i < 4; i++) {
            float phase = i == 0 || i == 3 ? 0 : (float)Math.PI;
            legs[i].xRot = Mth.cos(state.walkAnimationPos * .6662F + phase) * 1.4F * state.walkAnimationSpeed;
        }
    }
}