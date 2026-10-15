package naturality.client.sky.overworld.bloodmoon.mixins;

import naturality.client.sky.overworld.bloodmoon.BloodMoonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SkyRenderer.class)
@org.jspecify.annotations.NullMarked
public abstract class BloodMoonMixin {
    private static float partial() { return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false); }
    @ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"), index = 2)
    private float naturality$moonSize(float size) { return size * BloodMoonClient.scale(partial()); }
    @ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"), index = 1)
    private Vector4f naturality$redMoon(Vector4f color) {
        float amount = BloodMoonClient.sample(partial());
        return new Vector4f(color.x, color.y * (1 - amount), color.z * (1 - amount), color.w);
    }
}
