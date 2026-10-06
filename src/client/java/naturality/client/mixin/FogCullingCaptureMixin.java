package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.fog.FogCulling;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FogRenderer.class)
public abstract class FogCullingCaptureMixin {
    @Shadow private static boolean fogEnabled;

    @WrapMethod(method = "setupFog")
    private FogData naturality$capture(Camera camera, int distance, DeltaTracker delta, float darken,
            ClientLevel level, Operation<FogData> original) {
        FogData fog = original.call(camera, distance, delta, darken, level);
        FogCulling.capture(level, camera, fog, fogEnabled);
        return fog;
    }
}
