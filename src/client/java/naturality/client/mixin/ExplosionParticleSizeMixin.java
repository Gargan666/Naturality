package naturality.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve the server's explosion strength when creating its client visual. */
@Mixin(ClientPacketListener.class)
public abstract class ExplosionParticleSizeMixin {
    @Redirect(method = "handleExplosion", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void naturality$explosionScale(ClientLevel level, ParticleOptions particle,
            double x, double y, double z, double vx, double vy, double vz, ClientboundExplodePacket packet) {
        if (naturality.config.Feature.VISUAL_OVERHAUL.enabled()
                && (particle.getType() == ParticleTypes.EXPLOSION || particle.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
            double baseScale = packet.radius() / 4.0;
            vx = baseScale < 1 ? Math.min(1, baseScale * 2) : baseScale;
            var impact = naturality.client.particle.FireballImpactClient.consume(level, x, y, z);
            if (impact != null) {
                x = impact.hitX(); y = impact.hitY(); z = impact.hitZ();
                vy = impact.face().get3DDataValue() + 1;
            }
        }
        level.addParticle(particle, x, y, z, vx, vy, vz);
    }
}
