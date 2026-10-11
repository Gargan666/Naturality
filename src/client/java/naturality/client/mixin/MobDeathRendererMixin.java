package naturality.client.mixin;

import naturality.client.entity.MobDeathState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class MobDeathRendererMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at=@At("TAIL"))
    private void naturality$death(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
        boolean ghast = entity instanceof Ghast || entity instanceof HappyGhast;
        // Reach the final mask on the last visible tick, before vanilla removes the corpse at 20.
        int progress = entity instanceof Mob && entity.deathTime > 0 && !ghast
            ? Math.round(Math.clamp((entity.deathTime + partial) / 19F, 0F, 1F) * 2047) : 0;
        ((MobDeathState) state).naturality$deathPixels(progress);
        naturality.client.particle.DeathSmoke.owner(state, entity);
    }

    @Inject(method="getOverlayCoords", at=@At("RETURN"), cancellable=true)
    private static void naturality$encode(LivingEntityRenderState state, float white, CallbackInfoReturnable<Integer> cir) {
        // Overlay U uses only four bits. Keep its tint and V while transporting the mask progress.
        cir.setReturnValue(cir.getReturnValue() | (((MobDeathState) state).naturality$deathPixels() << 4));
    }
}
