package naturality.client.mixin;

import naturality.client.fire.EntityFireState;
import naturality.client.fire.SoulFireTracker;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityFireExtractMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void naturality$bounds(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
        // Rain can clear vanilla's synchronized fire flag between damage ticks.
        // Contact with lava is still an active fire source and must remain visible.
        if (!entity.fireImmune() && entity.isInLava()) state.displayFireAnimation = true;
        var box = entity.getInterpolatedBoundingBox(delta).move(-state.x, -state.y, -state.z);
        ((EntityFireState) state).naturality$fireData(box,
            0xFF000000 | (entity.getUUID().hashCode() & 0x0FFFFF), SoulFireTracker.isSoulBurning(entity));
    }
}
