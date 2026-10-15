package naturality.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import naturality.client.particle.DeathSmoke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Samples the actual posed cubes, including death roll, baby scale and limb transforms. */
public final class DeathSmokeLayer<S extends @org.jspecify.annotations.NonNull LivingEntityRenderState, M extends @org.jspecify.annotations.NonNull EntityModel<? super S>> extends RenderLayer<S, M> {
    public DeathSmokeLayer(RenderLayerParent<S, M> parent) { super(parent); }
    @Override public void submit(PoseStack stack, SubmitNodeCollector collector, int light, S state, float yaw, float pitch) {
        var entity = DeathSmoke.owner(state);
        if (!(entity instanceof Mob) || state.isInvisible || entity.deathTime <= 0) return;
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        var points = new ArrayList<Vec3>();
        var root = getParentModel().root();
        root.visit(stack, (pose, path, _, cube) -> {
            var part = root;
            if (!part.visible) return;
            for (String name : path.split("/")) {
                if (name.isEmpty()) continue;
                part = part.getChild(name);
                if (!part.visible) return;
            }
            if (part.skipDraw) return;
            // Subdivide long/wide cubes so the torso cannot monopolize the smoke.
            int nx = Math.clamp((int)Math.ceil((cube.maxX - cube.minX) / 6), 1, 4);
            int ny = Math.clamp((int)Math.ceil((cube.maxY - cube.minY) / 6), 1, 4);
            int nz = Math.clamp((int)Math.ceil((cube.maxZ - cube.minZ) / 6), 1, 4);
            for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) {
                var p = new Vector3f((cube.minX + (cube.maxX-cube.minX)*(x+.5F)/nx)/16,
                    (cube.minY + (cube.maxY-cube.minY)*(y+.5F)/ny)/16,
                    (cube.minZ + (cube.maxZ-cube.minZ)*(z+.5F)/nz)/16);
                pose.pose().transformPosition(p);
                points.add(camera.add(p.x, p.y, p.z));
            }
        });
        // The first death sprite fills only 3x4 of its 8x8 cell; size the quad
        // generously enough that its visible portion blankets adjacent samples.
        if (!points.isEmpty()) DeathSmoke.capture(entity, points, Math.max(.4F, .65F * state.scale));
    }
}
