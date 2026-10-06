package naturality.client.mixin;

import naturality.client.fog.FogCulling;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityFogCullingMixin<T extends Entity> {
    @Shadow protected abstract AABB getBoundingBoxForCulling(T entity, float partialTick);
    @Shadow protected abstract boolean affectedByCulling(T entity);

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void naturality$hiddenInFog(@NonNull T entity, Frustum frustum, double x, double y, double z,
            float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !FogCulling.active() || !FogCulling.matches(entity.level())
                || !affectedByCulling(entity) || entity.isCurrentlyGlowing()) return;
        AABB bounds = getBoundingBoxForCulling(entity, partialTick);
        // Leashes and their holders can cross the fog boundary independently of this body.
        if (entity instanceof net.minecraft.world.entity.Leashable leashable) {
            Entity holder = leashable.getLeashHolder();
            if (holder != null) bounds = bounds.minmax(holder.getInterpolatedBoundingBox(partialTick));
        }
        if (FogCulling.hidden(bounds.minX - 2, bounds.minY - 2, bounds.minZ - 2,
                bounds.maxX + 2, bounds.maxY + 2, bounds.maxZ + 2))
            cir.setReturnValue(false);
    }
}
