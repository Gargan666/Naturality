package naturality.mixin;

import naturality.particle.FireballImpactPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LargeFireball.class)
public abstract class FireballImpactMixin {
    @Inject(method = "onHit", at = @At("HEAD"))
    private void naturality$impactFace(HitResult hit, CallbackInfo ci) {
        var fireball = (LargeFireball) (Object) this;
        if (!(hit instanceof BlockHitResult blockHit) || !(fireball.level() instanceof ServerLevel level)) return;
        var location = blockHit.getLocation();
        var payload = new FireballImpactPayload(level.dimension().identifier(), fireball.getX(), fireball.getY(),
            fireball.getZ(), location.x, location.y, location.z, blockHit.getDirection());
        for (var player : level.players()) {
            if (player.distanceToSqr(fireball) < 64 * 64 && ServerPlayNetworking.canSend(player, FireballImpactPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
