package naturality.client.entity;

import naturality.Naturality;
import naturality.entity.MaddenedZombie;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.model.monster.zombie.BabyZombieModel;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class MaddenedZombieRenderer extends AbstractZombieRenderer<MaddenedZombie, ZombieRenderState, ZombieModel<ZombieRenderState>> {
    public static final class State extends ZombieRenderState { public boolean enraged; }
    @Override public ZombieRenderState createRenderState() { return new State(); }
    @Override public void extractRenderState(MaddenedZombie entity, ZombieRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        ((State)state).enraged = entity.isEnraged();
    }
    @Override protected boolean isShaking(ZombieRenderState state) {
        return ((State)state).enraged || super.isShaking(state);
    }
    @Override protected void setupRotations(ZombieRenderState state, com.mojang.blaze3d.vertex.PoseStack pose, float body, float scale) {
        super.setupRotations(state, pose, body, scale);
        if (((State)state).enraged)
            pose.rotateDegrees(com.mojang.math.Axis.YP, (float)Math.cos(state.ageInTicks * 3.25F) * 8);
    }
    public MaddenedZombieRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(MaddenedZombieGeometry.createBodyLayer().bakeRoot()),
            new BabyZombieModel<>(MaddenedZombieGeometry.createBodyLayer().apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot()),
            ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), ZombieModel::new),
            ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR, context.getModelSet(), BabyZombieModel::new));
        addLayer(new EyesLayer<ZombieRenderState, ZombieModel<ZombieRenderState>>(this) {
            @Override public RenderType renderType() {
                return RenderTypes.eyes(Naturality.id("textures/entity/maddened_zombie_emissive.png"));
            }
        });
    }
    @Override public Identifier getTextureLocation(ZombieRenderState state) {
        return Naturality.id("textures/entity/maddened_zombie.png");
    }
}
