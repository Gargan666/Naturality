package naturality.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Native poof motion starting directly on the last rendered corpse. */
public final class DeathSmokeParticle extends ExplodeParticle {
    private final float nativeSize;
    private final float coverSize;
    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(ParticleTypes.POOF, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) -> {
                var entity = DeathSmoke.spawning;
                var shape = entity == null ? null : DeathSmoke.shape(entity);
                if (shape == null) return new ExplodeParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random);
                int index = DeathSmoke.index++;
                var point = shape.points().get(Math.min(shape.points().size()-1, (int)((index+.5)*shape.points().size()/20)));
                return new DeathSmokeParticle(level, vx, vy, vz, sprites, point, shape.radius());
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
