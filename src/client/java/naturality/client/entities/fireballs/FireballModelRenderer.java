package naturality.client.entities.fireballs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import naturality.Naturality;
import java.util.ArrayDeque;
import naturality.entities.fireballs.FireballImpactPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/** The supplied Blockbench cubes, split into a solid core and scrolling shell. */
public final class FireballModelRenderer<T extends @org.jspecify.annotations.NonNull AbstractHurtingProjectile> extends EntityRenderer<T, FireballModelRenderer.State> {
    private static final ModelPart CORE = core();
    private final float scale;
    private final boolean fireShell;

    public static final class State extends EntityRenderState {
        public float yaw;
        public float pitch;
    }

    public FireballModelRenderer(EntityRendererProvider.Context context, float scale) {
        this(context, scale, true);
    }

    public FireballModelRenderer(EntityRendererProvider.Context context, float scale, boolean fireShell) {
        super(context);
        this.scale = scale;
        this.fireShell = fireShell;
    }
    public boolean hasFireShell() { return fireShell; }

    private static ModelPart core() {
        MeshDefinition mesh = new MeshDefinition();
        // Center the original (-4,-8,-4) core on the shared pivot.
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create()
            .texOffs(0, 20).addBox(-4, -4, -4, 8, 8, 8), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.displayFireAnimation = false;
        state.yaw = entity.getYRot(partialTick);
        state.pitch = entity.getXRot(partialTick);
    }

    /** One whole source texel every two ticks; wrapping keeps the render-type set bounded. */
    public static float fireOffset(float age) {
        return Math.floorMod((int)Math.floor(age / 2), 44) / 44.0F;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.isInvisible) {
            pose.pushPose();
            pose.translate(0, state.boundingBoxHeight / 2, 0);
            pose.rotate(Axis.YP.rotationDegrees(-state.yaw));
            pose.rotate(Axis.XP.rotationDegrees(state.pitch));
            // Local forward is +Z: positive X tumbles the top forward, negative Y turns left.
            pose.rotate(Axis.YP.rotationDegrees(-state.ageInTicks * 6));
            pose.rotate(Axis.XP.rotationDegrees(state.ageInTicks * 9));
            pose.scale(-scale, -scale, scale);
            submitPart(CORE, pose, collector, RenderTypes.entityCutout(Naturality.id(fireShell ? "textures/entity/fireball.png" : "textures/entity/fireball_dragon.png")));
            if (fireShell) FireballShell.submit(pose, collector, state.ageInTicks);
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }

    private static void submitPart(ModelPart part, PoseStack pose, net.minecraft.client.renderer.OrderedSubmitNodeCollector collector,
            net.minecraft.client.renderer.rendertype.RenderType type) {
        collector.submitCustomGeometry(pose, type, (submitted, buffer) -> {
            PoseStack modelPose = new PoseStack();
            modelPose.last().pose().set(submitted.pose());
            modelPose.last().normal().set(submitted.normal());
            part.render(modelPose, buffer, 15728880, OverlayTexture.NO_OVERLAY);
        });
    }

    /** Short-lived impact metadata paired with the immediately following explosion. */
    @org.jspecify.annotations.NullMarked
    public static final class FireballImpactClient {
        private record Pending(FireballImpactPayload payload, long expires) {}
        private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
        private static @Nullable ClientLevel world;
        private FireballImpactClient() {}

        public static void initialize() {
            ClientPlayNetworking.registerGlobalReceiver(FireballImpactPayload.TYPE, (payload, context) -> context.client().execute(() -> {
                if (!naturality.config.startup.FeatureConfig.Feature.EXPLOSION_PARTICLES.enabled()) return;
                var level = context.client().level;
                if (level == null || !level.dimension().identifier().equals(payload.dimension())) return;
                sync(level);
                if (PENDING.size() >= 64) PENDING.removeFirst();
                PENDING.addLast(new Pending(payload, level.getGameTime() + 20));
            }));
        }

        private static void sync(ClientLevel level) {
            if (world != level) { PENDING.clear(); world = level; }
            PENDING.removeIf(pending -> pending.expires < level.getGameTime());
        }

        public static @Nullable FireballImpactPayload consume(ClientLevel level, double x, double y, double z) {
            sync(level);
            var iterator = PENDING.iterator();
            while (iterator.hasNext()) {
                var payload = iterator.next().payload;
                double dx = payload.x() - x, dy = payload.y() - y, dz = payload.z() - z;
                if (dx * dx + dy * dy + dz * dz < 0.000001) {
                    iterator.remove();
                    return payload;
                }
            }
            return null;
        }
    }
}
