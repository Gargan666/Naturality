package naturality.client.portal;

import java.util.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.phys.Vec3;

/** Reuses actual baked fire/portal geometry and animated atlas UVs, rather than approximating a flame sprite. */
public final class PortalOpeningRenderState extends QuadParticleRenderState {
    private record Draw(BakedQuad quad, Vec3 offset, int color) { }
    private final Map<SingleQuadParticle.Layer, List<Draw>> draws = new HashMap<>();
    private record FireDraw(float[] positions, float[] uv, int color) { }
    private final List<FireDraw> fires = new ArrayList<>();
    public void addFire(net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
            net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState block, Vec3 offset, float alpha) {
        var emitter = net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
            float[] positions = new float[12], uv = new float[8];
            for (int i = 0; i < 4; i++) {
                positions[i * 3] = (float) (q.x(i) + offset.x);
                positions[i * 3 + 1] = (float) (q.y(i) + offset.y);
                positions[i * 3 + 2] = (float) (q.z(i) + offset.z);
                uv[i * 2] = q.u(i); uv[i * 2 + 1] = q.v(i);
            }
            fires.add(new FireDraw(positions, uv, (Math.round(alpha * 255) << 24)
                | (naturality.client.fire.ProceduralFire.seedColor(pos) & 0xFFFFFF)));
        });
        model.emitQuads(emitter, level, pos, block, net.minecraft.util.RandomSource.create(block.getSeed(pos)), _ -> false);
    }
    private final int brightnessRange = PortalTextureBrightness.packedRange(net.minecraft.client.Minecraft.getInstance());
    public void addModelQuad(SingleQuadParticle.Layer layer, BakedQuad quad, Vec3 offset, int color) {
        draws.computeIfAbsent(layer, _ -> new ArrayList<>()).add(new Draw(quad, offset, color));
    }
    @Override public Set<SingleQuadParticle.Layer> layers() {
        var layers = new HashSet<>(draws.keySet());
        if (!fires.isEmpty()) layers.add(PortalOpeningRenderLayer.FIRE);
        return layers;
    }
    @Override public boolean isEmpty() { return draws.isEmpty() && fires.isEmpty(); }
    @Override public void clear() { draws.clear(); fires.clear(); }
    @Override public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
        if (!isEmpty()) collector.submitQuadParticleGroup(this);
    }
    @Override public void buildLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer) {
        if (layer == PortalOpeningRenderLayer.FIRE) for (var fire : fires) for (int i = 0; i < 4; i++) {
            buffer.addVertex(fire.positions[i * 3], fire.positions[i * 3 + 1], fire.positions[i * 3 + 2])
                .setUv(fire.uv[i * 2], fire.uv[i * 2 + 1]).setColor(fire.color).setLight(0xF000F0);
        }
        for (Draw draw : draws.getOrDefault(layer, List.of())) {
            for (int i = 0; i < 4; i++) {
                var p = draw.quad.position(i);
                long uv = draw.quad.packedUV(i);
                buffer.addVertex((float) (p.x() + draw.offset.x), (float) (p.y() + draw.offset.y),
                    (float) (p.z() + draw.offset.z)).setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
                    .setColor(draw.color).setLight(brightnessRange);
            }
        }
    }
}
