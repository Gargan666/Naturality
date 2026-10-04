package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.villager.FirstPersonRodTip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class FirstPersonRodFrameMixin {
    @Shadow private void bobHurt(CameraRenderState camera, PoseStack pose) { throw new AssertionError(); }
    @Shadow private void bobView(CameraRenderState camera, PoseStack pose) { throw new AssertionError(); }
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void naturality$measureHand(CallbackInfo ci) {
        FirstPersonRodTip.worldTip = null;
        naturality.client.villager.PlayerRodTips.clear();
        var client = Minecraft.getInstance();
        if (client.player == null || (!client.player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD)
                && !client.player.getOffhandItem().is(net.minecraft.world.item.Items.FISHING_ROD))) return;
        var renderer = client.gameRenderer;
        var frame = renderer.gameRenderState();
        var player = frame.levelRenderState.playerRenderState;
        var camera = frame.levelRenderState.cameraRenderState;
        if (!frame.optionsRenderState.cameraType.isFirstPerson() || player.firstPersonHandsAndItems == null)
            return;
        FirstPersonRodTip.viewTip = null;
        FirstPersonRodTip.measuring = true;
        try {
            renderer.firstPersonHandsAndItemsRenderer.submitHandsWithItems(camera.cameraEntityPartialTicks,
                new PoseStack(), new SubmitNodeStorage(), player, player.firstPersonHandsAndItems);
        } finally {
            FirstPersonRodTip.measuring = false;
        }
        var tip = FirstPersonRodTip.viewTip;
        if (tip != null) {
            PoseStack bob = new PoseStack();
            bobHurt(camera, bob);
            if (frame.optionsRenderState.bobView) bobView(camera, bob);
            tip.mulPosition(bob.last().pose());
            float hudScale = (float)(1.0 / Math.tan(Math.toRadians(camera.hudFov) / 2));
            float scale = hudScale / camera.projectionMatrix.m11();
            // Small screen-space correction toward the visible edge of the tip.
            tip.x = (tip.x - .025F) * scale;
            tip.y *= scale;
            FirstPersonRodTip.pixelSize *= scale;
            // World projection also contains bob. Undo it after converting
            // hand FOV to world FOV; the two transforms do not commute.
            tip.mulPosition(new org.joml.Matrix4f(bob.last().pose()).invert());
            tip.rotate(camera.orientation);
            FirstPersonRodTip.worldTip = renderer.mainCamera().position().add(new Vec3(tip));
        }
    }
}
