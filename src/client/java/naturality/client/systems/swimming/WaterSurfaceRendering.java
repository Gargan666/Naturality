package naturality.client.systems.swimming;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.systems.swimming.WaterSurface;
import naturality.client.entities.maddened.MaddenedCreeperModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.animal.armadillo.ArmadilloModel;
import net.minecraft.client.model.animal.camel.CamelModel;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.animal.feline.AbstractFelineModel;
import net.minecraft.client.model.animal.fox.FoxModel;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.animal.llama.LlamaModel;
import net.minecraft.client.model.animal.rabbit.RabbitModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.hoglin.HoglinModel;
import net.minecraft.client.model.monster.ravager.RavagerModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/** Visual-only surface lean about eye level; never changes the camera or collision dimensions. */
@org.jspecify.annotations.NullMarked
public final class WaterSurfaceRendering {
    private WaterSurfaceRendering() { }
    public interface State {
        Vec3 naturality$surfaceLean();
        void naturality$surfaceLean(Vec3 lean);
        float naturality$surfaceEyes();
        void naturality$surfaceEyes(float height);
        float naturality$surfaceStroke();
        void naturality$surfaceStroke(float stroke);
        boolean naturality$surfaceFloating();
        void naturality$surfaceFloating(boolean floating);
    }
    public static void extract(LivingEntity entity, LivingEntityRenderState render, float partial, @Nullable EntityModel<?> model) {
        var target = (State)render;
        if (WaterSurface.riptiding(entity)) {
            target.naturality$surfaceLean(Vec3.ZERO);
            target.naturality$surfaceStroke(0);
            target.naturality$surfaceFloating(false);
            target.naturality$surfaceEyes(entity.getEyeHeight());
            return;
        }
        var source = WaterSurface.state(entity);
        var lean = source.previousLean.lerp(source.lean, partial);
        target.naturality$surfaceLean(fourLegged(model) ? lean.scale(-1) : lean);
        target.naturality$surfaceEyes(entity.getEyeHeight());
        target.naturality$surfaceStroke(source.previousStroke + (source.stroke - source.previousStroke) * partial);
        target.naturality$surfaceFloating(WaterSurface.active(entity));
    }

    private static boolean fourLegged(@Nullable EntityModel<?> model) {
        // Several four-legged families have their own animation bases instead of QuadrupedModel.
        // Checking base models also includes their baby variants and modded subclasses.
        return model instanceof QuadrupedModel<?> || model instanceof AbstractEquineModel<?>
            || model instanceof AbstractFelineModel<?> || model instanceof WolfModel || model instanceof FoxModel
            || model instanceof LlamaModel || model instanceof CamelModel || model instanceof ArmadilloModel
            || model instanceof RabbitModel || model instanceof FrogModel || model instanceof HoglinModel
            || model instanceof RavagerModel || model instanceof CreeperModel || model instanceof MaddenedCreeperModel;
    }
    public static void rotate(LivingEntityRenderState render, PoseStack pose) {
        // Player rendering blends both pivots/orientations in one transform, avoiding stacked tilt.
        if (render instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState) return;
        var state = (State)render;
        var lean = state.naturality$surfaceLean();
        double angle = lean.length();
        if (angle < 1.0e-6 || render.deathTime > 0 || render.isUpsideDown) return;
        double pivot = state.naturality$surfaceEyes() / render.scale;
        pose.translate(0, pivot, 0);
        pose.rotate(new Quaternionf().rotationAxis((float)Math.min(angle, Math.toRadians(20)),
            (float)(lean.x / angle), 0, (float)(lean.z / angle)));
        pose.translate(0, -pivot, 0);
    }
    public static void leg(ModelPart leg, LivingEntityRenderState state, float phase) {
        float blend = ((State)state).naturality$surfaceStroke();
        float kick = 0.3F * (float)Math.cos(state.walkAnimationPos / 3 + phase);
        leg.xRot += (kick - leg.xRot) * blend;
    }
}
