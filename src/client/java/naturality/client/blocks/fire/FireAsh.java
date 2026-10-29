package naturality.client.blocks.fire;

import java.util.ArrayList;
import naturality.blocks.fire.FirePropagation.AshPayload;
import naturality.client.api.rendering.AtlasLocations;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Burn-driven ash reuses the vanilla ash sprite and the procedural fire's authored palette. */
@NullMarked
public final class FireAsh {
    private static final int LIMIT = 512, AMBIENT_LIMIT = 384, PER_TICK = 96;
    private static final SpriteId SPRITE = new SpriteId(AtlasLocations.PARTICLES, Identifier.withDefaultNamespace("generic_0"));
    private static final ArrayList<AshParticle> particles = new ArrayList<>();
    private static @Nullable ClientLevel world;
    private static long budgetTick = Long.MIN_VALUE;
    private static int spawnedThisTick;

    private FireAsh() { }

    public static void initialize() {
        if (!naturality.config.startup.FeatureConfig.Feature.FIRE_PROPAGATION.enabled()) return;
        ClientPlayNetworking.registerGlobalReceiver(AshPayload.TYPE, (payload, context) -> receive(context.client(), payload));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            sync(client.level);
            particles.removeIf(particle -> !particle.isAlive());
        });
    }

    public static void particlesCleared() {
        for (var particle : particles) particle.remove();
        particles.clear();
    }

    private static void sync(@Nullable ClientLevel level) {
        if (world == level) return;
        particlesCleared(); world = level;
        budgetTick = Long.MIN_VALUE; spawnedThisTick = 0;
    }

    public static void receive(Minecraft client, AshPayload payload) {
        ClientLevel level = client.level;
        sync(level);
        if (level == null || !level.dimension().identifier().equals(payload.dimension())) return;
        var pos = payload.pos();
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                || client.gameRenderer.mainCamera().position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 80 * 80) return;
        particles.removeIf(particle -> !particle.isAlive());
        if (budgetTick != level.getGameTime()) { budgetTick = level.getGameTime(); spawnedThisTick = 0; }
        int count = payload.burst() ? 24 : ambientCount(payload.burned());
        var setting = client.options.particles().get();
        if (setting == net.minecraft.server.level.ParticleStatus.DECREASED) count /= 2;
        if (setting == net.minecraft.server.level.ParticleStatus.MINIMAL) count /= 4;
        int cap = payload.burst() ? LIMIT : AMBIENT_LIMIT;
        count = Math.min(count, Math.min(cap - particles.size(), PER_TICK - spawnedThisTick));
        if (count <= 0) return;
        var sprite = client.getAtlasManager().get(SPRITE);
        for (int i = 0; i < count; i++) {
            double radius = payload.burst() ? .65 : 7;
            double x = pos.getX() + .5 + (level.getRandom().nextDouble() - .5) * radius * 2;
            double y = pos.getY() + 1.05 + level.getRandom().nextDouble() * (payload.burst() ? .4 : 5);
            double z = pos.getZ() + .5 + (level.getRandom().nextDouble() - .5) * radius * 2;
            var particle = new AshParticle(level, x, y, z, sprite, payload.burst());
            if (!level.noCollision(particle.getBoundingBox())) continue;
            client.particleEngine.add(particle); particles.add(particle); spawnedThisTick++;
        }
    }

    public static int ambientCount(long burned) {
        return burned < 16 ? 0 : Math.min(12, 2 + (int) (Math.min(burned, 320) / 32));
    }

    public static int liveCount() { return particles.size(); }

    public static final class AshParticle extends SingleQuadParticle {
        private final double anchorX, anchorY, anchorZ, phase;
        private final int period;

        private AshParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, boolean burst) {
            super(level, x, y, z, sprite);
            anchorX = x; anchorY = y + (burst ? 1.2 : 0); anchorZ = z;
            phase = random.nextDouble() * Math.PI * 2;
            period = 60 + random.nextInt(61);
            lifetime = 180 + random.nextInt(121);
            quadSize = 3 * (.025F + random.nextFloat() * .035F);
            setSize(.12F, .12F);
            xd = (random.nextDouble() - .5) * (burst ? .24 : .04);
            yd = burst ? .05 + random.nextDouble() * .06 : .005;
            zd = (random.nextDouble() - .5) * (burst ? .24 : .04);
            naturality.client.systems.weather.wind.WindRendering.WindParticleControl.mark(this, true);
            updateColor(0);
        }

        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (++age >= lifetime || !level.hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4)) { remove(); return; }
            double time = phase + age * .025;
            // Widen the local drift, then smoothly trade buoyancy for gentle settling.
            double settling = Math.clamp((age - 40) / 60.0, 0, 1);
            settling = settling * settling * (3 - 2 * settling);
            xd = xd * .9 + (anchorX + Math.sin(time) * 3 - x) * .0015;
            double hovering = yd * .9 + (anchorY + Math.sin(time * .7) * .5 - y) * .002;
            yd = Mth.lerp(settling, hovering, yd * .94 - .0036);
            zd = zd * .9 + (anchorZ + Math.cos(time * .83) * 3 - z) * .0015;
            move(xd, yd, zd);
            updateColor(0);
        }

        @Override public void move(double dx, double dy, double dz) {
            // Use the native shape collision query without Particle's permanent stop
            // flag: touching a ceiling must still allow the ash to settle later.
            var motion = net.minecraft.world.entity.Entity.collideBoundingBox(
                net.minecraft.world.phys.shapes.CollisionContext.positionContext(y),
                new net.minecraft.world.phys.Vec3(dx, dy, dz), getBoundingBox(), level, java.util.List.of());
            setBoundingBox(getBoundingBox().move(motion));
            setLocationFromBoundingbox();
            if (dy < 0 && Math.abs(motion.y - dy) > 1.0E-7) { remove(); return; }
            if (Math.abs(motion.x - dx) > 1.0E-7) xd = 0;
            if (Math.abs(motion.y - dy) > 1.0E-7) yd = 0;
            if (Math.abs(motion.z - dz) > 1.0E-7) zd = 0;
        }

        public double heat(float partialTick) {
            double wave = .5 - .5 * Math.cos(phase + (age + partialTick) * Math.PI * 2 / period);
            return wave * wave * wave;
        }

        private void updateColor(float partialTick) {
            double heat = heat(partialTick);
            int color = ProceduralFire.ashColor(heat);
            if (heat < .025) rCol = gCol = bCol = .25F;
            else {
                rCol = ((color >> 16) & 255) / 255F;
                gCol = ((color >> 8) & 255) / 255F;
                bCol = (color & 255) / 255F;
            }
            alpha = Math.min(1, (age + partialTick + 1) / 10F) * Math.min(1, (lifetime - age - partialTick) / 40F);
        }

        @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
                net.minecraft.client.Camera camera, float partialTick) {
            updateColor(partialTick);
            var eye = camera.position();
            var rotation = new org.joml.Quaternionf();
            naturality.client.particles.smoke.SmokeParticle.faceCameraPosition(rotation,
                eye.x - Mth.lerp(partialTick, xo, x), eye.y - Mth.lerp(partialTick, yo, y), eye.z - Mth.lerp(partialTick, zo, z));
            extractRotatedQuad(state, camera, rotation, partialTick);
        }

        @Override protected int getLightCoords(float partialTick) {
            int light = super.getLightCoords(partialTick);
            int glow = (int) Math.round(heat(partialTick) * 240);
            return (light & 0xffff0000) | Math.max(light & 0xffff, glow);
        }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }
}
