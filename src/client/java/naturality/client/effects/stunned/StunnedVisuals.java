package naturality.client.effects.stunned;

import naturality.Naturality;
import naturality.client.api.rendering.AtlasLocations;
import naturality.effects.stunned.Stunned;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;

/** Effect-bound head motion and particle cards using the supplied circle and star textures. */
@NullMarked
public final class StunnedVisuals {
    private static final SpriteId CIRCLE = new SpriteId(AtlasLocations.PARTICLES, Naturality.id("particle/stunned_circle"));
    private static final SpriteId STAR = new SpriteId(AtlasLocations.PARTICLES, Naturality.id("particle/stunned_star"));

    private StunnedVisuals() {}

    public interface RenderState {
        boolean naturality$stunned();
        void naturality$setStunned(boolean stunned);
    }

    public interface AnimatedModel {
        HeadPose naturality$stunnedHeadPose();
    }

    /** One additive tilt per freshly prepared pose, shared by body, equipment and layer rendering. */
    public static final class HeadPose {
        private boolean active;
        private boolean applied;
        private float phase;

        public void begin(Object state) {
            active = state instanceof RenderState stunned && stunned.naturality$stunned();
            phase = state instanceof EntityRenderState entity ? entity.ageInTicks * ((float) Math.PI / 10) : 0;
            applied = false;
        }

        public void apply(Model<?> model) {
            if (!active || applied) return;
            applied = true;
            // A constant 12-degree lean whose axis completes one circle each second.
            var tilt = new Quaternionf().rotationAxis((float) Math.toRadians(12),
                (float) Math.cos(phase), 0, (float) Math.sin(phase));
            if (model instanceof HeadedModel headed) {
                headed.getHead().rotateBy(tilt);
                return;
            }
            // Equine head_parts carries the head, mane and bridle together.
            var head = model.getChildPart("head_parts");
            if (head == null) head = model.getChildPart("head");
            if (head != null) head.rotateBy(tilt);
            // Withers have three independent heads instead of one head parent.
            for (var name : new String[]{"center_head", "left_head", "right_head"}) {
                var extraHead = model.getChildPart(name);
                if (extraHead != null) extraHead.rotateBy(tilt);
            }
        }
    }

    public static void extract(ParticlesRenderState output, Frustum frustum, Camera camera, float partialTick) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null) return;
        var state = new QuadParticleRenderState();
        var circle = client.getAtlasManager().get(CIRCLE);
        var star = client.getAtlasManager().get(STAR);
        float angle = (float) (((level.getGameTime() % 40) + partialTick) * Math.PI / 20);
        var horizontal = new Quaternionf().rotationY(angle).rotateX((float) (Math.PI / 2));
        var underside = new Quaternionf(horizontal).rotateY((float) Math.PI);
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || !entity.isAlive() || !Stunned.active(entity)) continue;
            var bounds = entity.getBoundingBox();
            float radius = Math.max(0.4F, entity.getBbWidth() * 0.6F);
            var anchor = entity.getPosition(partialTick).add(0, bounds.getYsize() + 0.2, 0);
            if (!frustum.isVisible(new net.minecraft.world.phys.AABB(anchor, anchor).inflate(radius + 0.2))) continue;
            var relative = anchor.subtract(camera.position());
            add(state, circle, relative, horizontal, radius, false);
            add(state, circle, relative, underside, radius, true);
            // The supplied 8x8 circle leaves opposite gaps at pixel centers (1,1) and (6,6).
            double gap = radius * 0.625;
            for (int side = -1; side <= 1; side += 2) {
                double dx = side * gap * (-cosine + sine);
                double dz = side * gap * (sine + cosine);
                var starPosition = relative.add(dx, 0, dz);
                var toCamera = starPosition.scale(-1).normalize();
                var facing = new Quaternionf().rotationYXZ((float) Math.atan2(toCamera.x, toCamera.z),
                    (float) -Math.asin(Math.clamp(toCamera.y, -1, 1)), 0);
                add(state, star, starPosition, facing, radius * 0.3F, false);
            }
        }
        if (!state.isEmpty()) output.add(state);
    }

    private static void add(QuadParticleRenderState state, TextureAtlasSprite sprite, Vec3 center,
            Quaternionf rotation, float size, boolean reverseU) {
        state.add(SingleQuadParticle.Layer.TRANSLUCENT, (float) center.x, (float) center.y, (float) center.z,
            rotation.x, rotation.y, rotation.z, rotation.w, size,
            reverseU ? sprite.getU1() : sprite.getU0(), reverseU ? sprite.getU0() : sprite.getU1(),
            sprite.getV0(), sprite.getV1(), 0xFFFFFFFF, 0xF000F0);
    }
}
