package naturality.test;

import naturality.client.particle.WindParticleControl;
import naturality.weather.WeatherPayload;
import naturality.weather.WeatherState;
import naturality.weather.WeatherSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

final class ParticleWindChecks {
    private static final class Probe extends Particle {
        Probe(Minecraft client) { super(client.level, 0, 110, 0); }
        @Override public ParticleRenderType getGroup() { return ParticleRenderType.SINGLE_QUADS; }
        @Override public void tick() {
            // Deliberately bypass super.tick and replace velocity each frame, like custom particles.
            xo = x; yo = y; zo = z;
            xd = .2; yd = .1; zd = -.1;
            move(xd, yd, zd);
        }
    }
    static void run(Minecraft client) {
        var dimension = client.level.dimension().identifier();
        var saved = WeatherSystem.state(client.level);
        var waterPos = new BlockPos(0, 110, 0);
        var oldBlock = client.level.getBlockState(waterPos);
        var group = new ParticleGroup<Probe>(client.particleEngine) {
            @Override public ParticleGroupRenderState extractRenderState(Frustum f, Camera c, float t) { return null; }
        };
        try {
            WeatherSystem.receive(new WeatherPayload(dimension, true, new WeatherState(0, 75, 50, 0)));
            var p = new Probe(client);
            group.add(p);
            group.tickParticles();
            near(p.getBoundingBox().getCenter().x, .207, "Wind adds to existing horizontal motion");
            near(p.getBoundingBox().minY, 110.1, "Wind preserves vertical motion");
            near(p.getBoundingBox().getCenter().z, -.1, "Wind preserves crosswind motion");
            group.tickParticles();
            near(p.getBoundingBox().getCenter().x, .2 + .007 + .2 + .007 * 1.96,
                "Custom velocity replacement cannot erase accumulated wind");
            client.level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
            p.setPos(.2, 110.2, .5);
            group.tickParticles();
            near(p.getBoundingBox().getCenter().x, .4, "Underwater particles receive no wind drift");
            p.setPos(0, 112, 0);
            group.tickParticles();
            near(p.getBoundingBox().getCenter().x, .207, "Entering water clears previous wind momentum");
            WindParticleControl.mark(p, true);
            p.setPos(0, 112, 0);
            group.tickParticles();
            near(p.getBoundingBox().getCenter().x, .2, "Tagged particles retain only their own motion");
            for (var type : new net.minecraft.core.particles.SimpleParticleType[] {
                    ParticleTypes.FLAME, ParticleTypes.SMALL_FLAME, ParticleTypes.SOUL_FIRE_FLAME,
                    ParticleTypes.TOTEM_OF_UNDYING, ParticleTypes.ENCHANT}) {
                var particle = client.particleEngine.createParticle(type, 0, 112, 0, .1, .1, .1);
                if (particle == null || !WindParticleControl.immune(particle))
                    throw new AssertionError("Flame and magical particles must remain wind immune: " + type);
                particle.remove();
            }
        } finally {
            client.level.setBlock(waterPos, oldBlock, 3);
            WeatherSystem.receive(new WeatherPayload(dimension, saved != null,
                saved != null ? saved : new WeatherState(0, 0, 50, 0)));
        }
    }
    private static void near(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > .000001) throw new AssertionError(message + ": " + actual);
    }
}
