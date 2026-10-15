package naturality.client.entities.maddened;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.Naturality;
import naturality.entities.maddened.MaddenedCreepers.MaddenedCreeper;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class MaddenedCreeperRenderer extends MobRenderer<MaddenedCreeper, CreeperRenderState, MaddenedCreeperModel> {
    public MaddenedCreeperRenderer(EntityRendererProvider.Context context) {
        super(context, new MaddenedCreeperModel(), .5F);
        addLayer(new EyesLayer<CreeperRenderState, MaddenedCreeperModel>(this) {
            @Override public RenderType renderType() {
                return RenderTypes.eyes(Naturality.id("textures/entity/maddened_creeper_emissive.png"));
            }
        });
        addLayer(new EnergySwirlLayer<CreeperRenderState, MaddenedCreeperModel>(this) {
            private final MaddenedCreeperModel armor = new MaddenedCreeperModel();
            protected boolean isPowered(CreeperRenderState state) { return state.isPowered; }
            protected float xOffset(float age) { return age * .01F; }
            protected Identifier getTextureLocation() {
                return Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
            }
            protected MaddenedCreeperModel model() { return armor; }
        });
    }

    public static float idlePulse(float age) {
        return .32F + .10F * Mth.sin(age * .35F);
    }

    @Override public CreeperRenderState createRenderState() { return new CreeperRenderState(); }
    @Override public Identifier getTextureLocation(CreeperRenderState state) {
        return Naturality.id("textures/entity/maddened_creeper.png");
    }
    @Override public void extractRenderState(MaddenedCreeper entity, CreeperRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.swelling = entity.getSwelling(partial);
        state.isPowered = entity.isPowered();
    }
    @Override protected void scale(CreeperRenderState state, PoseStack pose) {
        float swelling = Math.max(state.swelling, idlePulse(state.ageInTicks));
        float wobble = 1 + Mth.sin(swelling * 100) * swelling * .01F;
        float growth = Mth.clamp(swelling, 0, 1);
        growth *= growth;
        growth *= growth;
        // Stronger breathing accompanies the later idle-fuse stage.
        float breathing = 1 + idlePulse(state.ageInTicks) * .08F;
        pose.scale((1 + growth * .4F) * wobble * breathing,
            (1 + growth * .1F) / wobble / breathing, (1 + growth * .4F) * wobble * breathing);
    }
    @Override protected float getWhiteOverlayProgress(CreeperRenderState state) {
        // Keep the constant pulse geometric; white fuse flashes only accompany real ignition.
        return (int)(state.swelling * 10) % 2 == 0 ? 0 : Mth.clamp(state.swelling, .5F, 1);
    }
}
