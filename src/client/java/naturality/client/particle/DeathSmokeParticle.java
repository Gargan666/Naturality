package naturality.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Native poof motion starting directly on the last rendered corpse. */
public final class DeathSmokeParticle extends ExplodeParticle {
    private float nativeSize;
    private float coverSize;
    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(ParticleTypes.POOF, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) -> {
                var entity = DeathSmoke.spawning;
                var shape = entity == null ? null : DeathSmoke.shape(entity);
                boolean ghast = naturality.ghast.GhastMotion.supported(entity);
                if (shape == null && !ghast) return new ExplodeParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random);
                int index = DeathSmoke.index++;
                int count = ghast ? 30 : 20;
                var point = shape == null ? new Vec3(x,y,z) : shape.points().get(Math.min(shape.points().size()-1, (int)((index+.5)*shape.points().size()/count)));
                float radius = shape == null ? .65F : shape.radius();
                if (ghast && entity != null) radius = Math.max(radius,entity.getBbWidth()*.35F);
                var puff = new DeathSmokeParticle(level, vx, vy, vz, sprites, point, radius);
                if (ghast && index%3==0) { puff.nativeSize*=1.75F; puff.coverSize*=1.75F; puff.quadSize=puff.coverSize; }
                // Keep ghast coverage (including the larger third) for the whole animation.
                if (ghast) puff.nativeSize = puff.coverSize;
                return puff;
            });
    }
    public DeathSmokeParticle(ClientLevel level, double vx, double vy, double vz,
                              SpriteSet sprites, Vec3 origin, float radius) {
        super(level, origin.x, origin.y, origin.z, vx, vy, vz, sprites);
        nativeSize = quadSize;
        coverSize = Math.max(nativeSize, radius);
        quadSize = coverSize;
    }
    @Override public void tick() {
        super.tick();
        float after = DeathSmoke.coverage(age);
        quadSize = nativeSize + (coverSize-nativeSize)*after;
    }
}
