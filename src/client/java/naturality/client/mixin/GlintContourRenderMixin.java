package naturality.client.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import naturality.client.glint.GlintContours;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class GlintContourRenderMixin {
    @SuppressWarnings("null") @Shadow @Final private LevelTargetBundle targets;
    @Unique private @org.jspecify.annotations.Nullable GpuBufferSlice naturality$contourFog;

    @Inject(method = "render", at = @At("HEAD"))
    private void naturality$begin(GraphicsResourceAllocator allocator, boolean outline,
            CameraRenderState camera, GpuBufferSlice fog, Vector4f fogColor,
            boolean sky, boolean consistentDepth, CallbackInfo ci) {
        GlintContours.beginFrame();
        naturality$contourFog = fog;
    }

    @Inject(method = "executeOutline", at = @At("TAIL"))
    private void naturality$render(CallbackInfo ci) {
        GlintContours.render(targets.main.get(), naturality$contourFog);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void naturality$end(CallbackInfo ci) { GlintContours.endFrame(); }

    @Inject(method = "close", at = @At("TAIL"))
    private void naturality$close(CallbackInfo ci) { GlintContours.close(); }
}
