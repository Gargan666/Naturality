package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import naturality.client.menu.MenuPreview;

import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class MenuSkyProjectionMixin {
    @Unique private @org.jspecify.annotations.Nullable ProjectionMatrixBuffer naturality$skyProjection;
    @WrapMethod(method = "render")
    private void naturality$sky(GpuBufferSlice fog, SkyRenderState state, Operation<Void> original) {
        if (!MenuPreview.active()) { original.call(fog, state); return; }
        var buffer = naturality$skyProjection;
        if (buffer == null) { buffer = new ProjectionMatrixBuffer("Menu native sky"); naturality$skyProjection = buffer; }
        var projection = MenuPreview.skyProjection();
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(buffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
            original.call(fog, state);
        } finally { RenderSystem.restoreProjectionMatrix(); }
    }
    @Inject(method = "close", at = @At("TAIL"))
    private void naturality$close(CallbackInfo ci) {
        if (naturality$skyProjection != null) naturality$skyProjection.close();
    }
}
