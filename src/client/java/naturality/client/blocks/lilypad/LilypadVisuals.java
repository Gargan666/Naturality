package naturality.client.blocks.lilypad;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import naturality.api.worlds.LoadedChunks;
import naturality.blocks.lilypad.GiantLilypadBlock;
import naturality.blocks.lilypad.LilypadMotion;
import naturality.client.fluids.water.WaterIntersection;
import naturality.client.fluids.water.WaterShaders.WaterSurface;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Captures resource-pack models once during meshing; animates their immutable quads per frame. */
@org.jspecify.annotations.NullMarked
public final class LilypadVisuals {
    private record Vertex(float x, float y, float z, float u, float v) {}
    private record Mesh(BlockState state, List<Vertex[]> quads, boolean[] mask) {}
    private record Ring(BlockPos origin, int width, boolean[] mask, float[] distances, long born) {}
    private record Draw(List<Vertex[]> quads, Vec3 origin, int light) {}
    private static final Map<BlockPos, Mesh> MESHES = new ConcurrentHashMap<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static @Nullable ClientLevel world;
    private LilypadVisuals() {}

    public static void initialize() {
        LilypadMotion.onTransition = LilypadVisuals::ripple;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.level) { world = client.level; MESHES.clear(); RINGS.clear(); }
            var level = world;
            if (level != null && !client.isPaused()) {
                LilypadMotion.clientContacts(level,level.entitiesForRendering());
                RINGS.removeIf(r -> level.getGameTime() - r.born >= 10);
            }
        });
        ModelLoadingPlugin.register(context -> {
            MESHES.clear();
            context.modifyBlockModelAfterBake().register(ModelModifier.WRAP_LAST_PHASE,
                (model, bake) -> bake.state().getBlock() instanceof LilyPadBlock ? new Model(model) : model);
        });
    }

    private static final class Model extends WrapperBlockStateModel {
        Model(BlockStateModel wrapped) { super(wrapped); }
        @Override public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) { return null; }
        @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                RandomSource random, Predicate<@Nullable Direction> cull) {
            if (MESHES.size() >= 65536 && !MESHES.containsKey(pos)) {
                wrapped.emitQuads(emitter, level, pos, state, random, cull); return;
            }
            var sprite = wrapped.particleMaterial(level, pos, state).sprite();
            var quads = new ArrayList<Vertex[]>();
            var mask = new boolean[256];
            emitter.pushTransform(q -> {
                var vertices = new Vertex[4];
                for (int i = 0; i < 4; i++) vertices[i] = new Vertex(q.x(i), q.y(i), q.z(i), q.u(i), q.v(i));
                quads.add(vertices);
                if (q.lightFace() == Direction.UP) {
                    var a = vertices[0]; var b = vertices[1]; var d = vertices[3];
                    double bx = b.x-a.x, bz = b.z-a.z, dx = d.x-a.x, dz = d.z-a.z;
                    double det = bx*dz-bz*dx;
                    if (Math.abs(det) > 1e-8) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        double px = (x+.5)/16-a.x, pz = (z+.5)/16-a.z;
                        double s = (px*dz-pz*dx)/det, t = (bx*pz-bz*px)/det;
                        if (s < 0 || s > 1 || t < 0 || t > 1) continue;
                        double u = a.u+s*(b.u-a.u)+t*(d.u-a.u), v = a.v+s*(b.v-a.v)+t*(d.v-a.v);
                        int tx = (int)((u-sprite.getU0())/(sprite.getU1()-sprite.getU0())*sprite.contents().width());
                        int ty = (int)((v-sprite.getV0())/(sprite.getV1()-sprite.getV0())*sprite.contents().height());
                        tx = Math.clamp(tx,0,sprite.contents().width()-1); ty = Math.clamp(ty,0,sprite.contents().height()-1);
                        mask[z*16+x] |= !sprite.contents().isTransparent(0,tx,ty);
                    }
                }
                return false;
            });
            try { wrapped.emitQuads(emitter, level, pos, state, random, cull); }
            finally { emitter.popTransform(); }
            MESHES.put(pos.immutable(), new Mesh(state, List.copyOf(quads), mask));
        }
    }

    private static void ripple(Level level, BlockPos origin) {
        if (level != world || RINGS.size() >= 64) return;
        int width = level.getBlockState(origin).getBlock() instanceof GiantLilypadBlock ? 32 : 16;
        boolean[] mask = new boolean[width*width];
        for (int z = 0; z < width/16; z++) for (int x = 0; x < width/16; x++) {
            var mesh = MESHES.get(origin.offset(x,0,z));
            if (mesh == null) return;
            for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++)
                mask[(z*16+v)*width+x*16+u] = mesh.mask[v*16+u];
        }
        // Calculate the outward distance field once per event, not once per frame.
        int size = width + 12;
        float[] distances = new float[size*size];
        var ring = new Ring(origin.immutable(), width, mask, distances, level.getGameTime());
        for (int z = -6; z < width+6; z++) for (int x = -6; x < width+6; x++) {
            double distance = 10;
            if (occupied(ring,x,z)) distance = 0;
            else for (int dz = -6; dz <= 6; dz++) for (int dx = -6; dx <= 6; dx++)
                if (occupied(ring,x+dx,z+dz)) distance = Math.min(distance,Math.hypot(dx,dz));
            distances[(z+6)*size+x+6] = (float)distance;
        }
        RINGS.add(ring);
    }

    public static void extract(ParticlesRenderState output, Frustum frustum, Camera camera, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null || level != world) return;
        var eye = camera.position();
        var draws = new ArrayList<Draw>();
        for (var entry : MESHES.entrySet()) {
            var pos = entry.getKey(); var mesh = entry.getValue();
            if (!LoadedChunks.has(level,pos)) { MESHES.remove(pos,mesh); continue; }
            if (!level.getBlockState(pos).equals(mesh.state)) { MESHES.remove(pos,mesh); continue; }
            if (!frustum.isVisible(new AABB(pos).inflate(.15))) continue;
            double dip = LilypadMotion.offset(level,pos,mesh.state,partial);
            var origin = Vec3.atLowerCornerOf(pos).subtract(eye).add(0,dip,0);
            draws.add(new Draw(mesh.quads,origin,LightCoordsUtil.getLightCoords(level,pos)));
        }
        if (!draws.isEmpty()) output.add(new Pads(List.copyOf(draws)));
        var ripples = new WaterIntersection();
        for (var ring : RINGS) {
            double age = level.getGameTime()-ring.born+partial;
            if (age < 0 || age >= 10 || ring.origin.distToCenterSqr(eye) > 32*32) continue;
            double radius = .5+age*.45;
            for (int z = -6; z < ring.width+6; z++) for (int x = -6; x < ring.width+6; x++) {
                if (occupied(ring,x,z)) continue;
                double distance = ring.distances[(z+6)*(ring.width+12)+x+6];
                float alpha = (float)(Math.max(0,1-Math.abs(distance-radius))*.35*(1-age/10));
                if (alpha <= 0) continue;
                BlockPos pos = ring.origin.offset(Math.floorDiv(x,16),-1,Math.floorDiv(z,16));
                if (!LoadedChunks.has(level,pos)) continue;
                var surface = WaterSurface.at(level,pos);
                int u = Math.floorMod(x,16), v = Math.floorMod(z,16);
                if (surface != null && surface.contains((u+.5)/16,(v+.5)/16))
                    ripples.ripplePixel(surface,u,v,eye,alpha,BiomeColors.getAverageWaterColor(level,pos),LightCoordsUtil.getLightCoords(level,pos));
            }
        }
        if (!ripples.isEmpty()) output.add(ripples);
    }
    private static boolean occupied(Ring r,int x,int z) {
        return x >= 0 && z >= 0 && x < r.width && z < r.width && r.mask[z*r.width+x];
    }
    private static final class Pads extends QuadParticleRenderState {
        private final List<Draw> draws;
        Pads(List<Draw> draws) { this.draws = draws; }
        @Override public boolean isEmpty() { return draws.isEmpty(); }
        @Override public void submit(net.minecraft.client.renderer.SubmitNodeCollector collector,
                net.minecraft.client.renderer.state.level.CameraRenderState camera) {
            if (!draws.isEmpty()) collector.submitQuadParticleGroup(this);
        }
        @Override public Set<SingleQuadParticle.Layer> layers() { return Set.of(SingleQuadParticle.Layer.OPAQUE_TERRAIN); }
        @Override public void buildLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer) {
            for (var draw : draws) for (var quad : draw.quads) for (var vertex : quad)
                buffer.addVertex((float)draw.origin.x+vertex.x,(float)draw.origin.y+vertex.y,(float)draw.origin.z+vertex.z,
                    -14647248,vertex.u,vertex.v,OverlayTexture.NO_OVERLAY,draw.light,0,1,0);
        }
    }
}
