package naturality.client.mixin;

import naturality.client.lighting.HardcoreDarkness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LevelRenderer.class)
public abstract class HardcoreCloudMixin {
    @ModifyArgs(method = "prepareTranslucents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CloudRenderer;prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V"))
    private void naturality$darkness(Args args) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null) return;
        float factor = HardcoreDarkness.clouds(level, client.gameRenderer.mainCamera(), args.<Float>get(6));
        int color = args.get(0);
        int red = (int) (((color >>> 16) & 255) * factor);
        int green = (int) (((color >>> 8) & 255) * factor);
        int blue = (int) ((color & 255) * factor);
        args.set(0, (color & 0xff000000) | (red << 16) | (green << 8) | blue);
    }
}
