package naturality.client.systems.swimming;

import naturality.systems.swimming.PlayerSwimming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Client swimming input, animations and the vanilla-style contextual charge bar. */
@org.jspecify.annotations.NullMarked
public final class PlayerSwimmingClient {
    private PlayerSwimmingClient() { }
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("hud/jump_bar_background");
    private static final Identifier PROGRESS = Identifier.withDefaultNamespace("hud/jump_bar_progress");
    private static final Identifier COOLDOWN = Identifier.withDefaultNamespace("hud/jump_bar_cooldown");

    public interface RenderState {
        float naturality$brace();
        void naturality$brace(float value);
        boolean naturality$physicalSwimming();
        void naturality$physicalSwimming(boolean value);
        float naturality$gentleStroke();
        void naturality$gentleStroke(float value);
        float naturality$strokePhase();
        void naturality$strokePhase(float value);
    }

    public static void animate(HumanoidModel<?> model, HumanoidRenderState state) {
        if (!(state instanceof RenderState swimming) || !swimming.naturality$physicalSwimming()) return;
        model.head.xRot = (float)Math.toRadians(state.xRot);
    }
    public static void gentleSwim(HumanoidModel<?> model, HumanoidRenderState state, float phase, float blend) {
        strokeLegs(model, phase, blend, model.leftLeg.xRot, model.rightLeg.xRot);
        if (blend <= 0 || state.isUsingItem) return;
        var swing = state.currentSwing;
        if ((swing == null || swing.hand().asArm(state.mainArm) != HumanoidArm.LEFT)
                && (state.leftArmPose == HumanoidModel.ArmPose.EMPTY || state.leftArmPose == HumanoidModel.ArmPose.ITEM))
            gentleArm(model.leftArm, blend, strokeAngles(phase, -1));
        if ((swing == null || swing.hand().asArm(state.mainArm) != HumanoidArm.RIGHT)
                && (state.rightArmPose == HumanoidModel.ArmPose.EMPTY || state.rightArmPose == HumanoidModel.ArmPose.ITEM))
            gentleArm(model.rightArm, blend, strokeAngles(phase, 1));
    }
    public static void brace(HumanoidModel<?> model, float blend) {
        model.body.xRot = Mth.rotLerpRad(blend, model.body.xRot, 0.5F);
        model.leftLeg.xRot *= 1 - blend;
        model.rightLeg.xRot *= 1 - blend;
        model.leftLeg.z += 4 * blend;
        model.rightLeg.z += 4 * blend;
        model.head.y += 4.2F * blend;
        model.body.y += 3.2F * blend;
        model.leftArm.y += 3.2F * blend;
        model.rightArm.y += 3.2F * blend;
    }
    public static void strokeLegs(HumanoidModel<?> model, float phase, float blend, float left, float right) {
        // Vanilla's crawl arms and kicks have different periods. Use one closed cycle so
        // wrapping the reversible arm clock never introduces a discontinuity in the legs.
        float kick = 0.3F * (float)Math.cos(phase);
        model.leftLeg.xRot = Mth.rotLerpRad(blend, left, -kick);
        model.rightLeg.xRot = Mth.rotLerpRad(blend, right, kick);
    }
    public static Vec3 strokeAngles(float phase, int side) {
        // Reach forward, sweep outward/down, then recover inward. Mirrored closed loops keep
        // both hands below the shoulders throughout the stroke instead of freezing a crawl pose.
        double reach = Math.cos(phase), sweep = Math.sin(phase);
        return new Vec3(-0.65 - 0.45 * reach, side * (0.35 + 0.35 * sweep), side * (0.425 - 0.275 * reach));
    }
    private static void gentleArm(ModelPart arm, float blend, Vec3 angles) {
        arm.xRot = Mth.rotLerpRad(blend, arm.xRot, (float)angles.x);
        arm.yRot = Mth.rotLerpRad(blend, arm.yRot, (float)angles.y);
        arm.zRot = Mth.rotLerpRad(blend, arm.zRot, (float)angles.z);
    }

    public static void input(LocalPlayer player) {
        // Preserve Shift in the normal input packet; water travel uses it as descent, not crouching.
        PlayerSwimming.state(player).input = player.input.keyPresses;
    }

    public static boolean showBar() {
        var player = Minecraft.getInstance().player;
        return player != null && PlayerSwimming.active(player) && PlayerSwimming.meter(player) != -1;
    }

    public static void bar(GuiGraphicsExtractor graphics) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;
        int left = (minecraft.getWindow().getGuiScaledWidth() - 182) / 2;
        int top = minecraft.getWindow().getGuiScaledHeight() - 29;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 182, 5);
        float charge = PlayerSwimming.meter(player);
        if (charge == -2) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, COOLDOWN, left, top, 182, 5);
        else {
            int width = (int)(Math.clamp(charge, 0, 1) * 182);
            if (width > 0) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, 182, 5, 0, 0, left, top, width, 5);
        }
    }
}
