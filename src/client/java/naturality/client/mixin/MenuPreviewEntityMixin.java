package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class MenuPreviewEntityMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void naturality$surfaceEntities(Entity entity, Frustum frustum, double x, double y, double z,
                                           float delta, long fade, CallbackInfoReturnable<Boolean> cir) {
        if (!MenuPreview.active()) return;
        var pos = entity.blockPosition();
        var center = MenuPreview.spawn();
        double dx = entity.getX() - center.getX(), dz = entity.getZ() - center.getZ();
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (naturality.client.menu.MenuWorld.camera().pitch() <= 0
                || dx * dx + dz * dz >= Math.pow(MenuPreview.radius() * .85, 2)
                || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                || entity.getBoundingBox().maxY < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 2)
            cir.setReturnValue(false);
    }
    @Inject(method = "extractVisibleBlockEntities", at = @At("HEAD"), cancellable = true)
    private void naturality$hideUndersideBlockEntities(net.minecraft.client.Camera camera, float partial,
            net.minecraft.client.renderer.state.level.LevelRenderState state, CallbackInfo ci) {
        if (MenuPreview.active() && naturality.client.menu.MenuWorld.camera().pitch() <= 0) ci.cancel();
    }
    @Inject(method = "extractVisibleBlockEntities", at = @At("TAIL"))
    private void naturality$hideBuriedBlockEntities(net.minecraft.client.Camera camera, float partial,
            net.minecraft.client.renderer.state.level.LevelRenderState state, CallbackInfo ci) {
        if (!MenuPreview.active()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var center = MenuPreview.spawn();
        double radius = MenuPreview.radius() * .85;
        state.blockEntityRenderStates.removeIf(rendered -> {
            var pos = rendered.blockPos;
            double dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
            var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
            return dx * dx + dz * dz >= radius * radius || chunk == null
                    || pos.getY() < naturality.client.menu.MenuFog.surfaceFloor(chunk, pos.getX(), pos.getZ());
        });
    }
}
