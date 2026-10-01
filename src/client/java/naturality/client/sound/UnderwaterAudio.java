package naturality.client.sound;

import naturality.client.fluid.WaterVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;

/** Client-thread listener state, published to OpenAL's sound thread. */
public final class UnderwaterAudio {
    private static volatile float amount;
    private UnderwaterAudio() {}
    public static float amount() { return amount; }
    public static float strength(float depth) {
        float t = Math.clamp((depth - 1.0F) / 3.0F, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
    public static void update() {
        var client = Minecraft.getInstance();
        amount = client.level != null && client.player != null
            && client.gameRenderer.mainCamera().getFluidInCamera() == FogType.WATER
            ? strength(WaterVisuals.cameraDepth()) : 0.0F;
    }
}
