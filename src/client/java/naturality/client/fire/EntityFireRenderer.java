package naturality.client.fire;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.AABB;

public final class EntityFireRenderer {
    public static final RenderType LAYER = RenderType.create("naturality_entity_fire", RenderSetup.builder(
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Naturality.id("pipeline/entity_fire"))
            .withVertexShader(Naturality.id("core/fire_fade"))
            .withFragmentShader(Naturality.id("core/entity_fire"))
            .withBindGroupLayout(ProceduralFire.LAYOUT).withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(ColorTargetState.DEFAULT).build()))
        .withTexture("Sampler0", naturality.client.AtlasLocations.BLOCKS).useLightmap().createRenderSetup());

    private EntityFireRenderer() { }
    public static AABB envelope(AABB box) {
        double above = Math.clamp(box.getYsize() * 0.12, 0.15, 0.35);
        return new AABB(box.minX - 0.025, box.minY, box.minZ - 0.025,
            box.maxX + 0.025, box.maxY + above, box.maxZ + 0.025);
    }
    public static void emit(FlameFeatureRenderer.Submit submit, VertexConsumer buffer, TextureAtlasSprite sprite) {
        var state = submit.entityRenderState();
        var data = (EntityFireState) state;
        AABB actual = data.naturality$fireBox();
        if (actual == null) actual = new AABB(-state.boundingBoxWidth / 2, 0, -state.boundingBoxWidth / 2,
            state.boundingBoxWidth / 2, state.boundingBoxHeight, state.boundingBoxWidth / 2);
        AABB b = envelope(actual);
        double[][] corners = {{b.minX,b.minZ}, {b.maxX,b.minZ}, {b.maxX,b.maxZ}, {b.minX,b.maxZ}};
        for (int side = 0; side < 4; side++) {
            var a = corners[side]; var c = corners[(side + 1) % 4];
            int seed = 0xFF000000 | ((data.naturality$fireSeed() + side * 7919) & 0x0FFFFF);
            for (int v = 0; v < 4; v++) {
                var p = v < 2 ? a : c;
                boolean top = v == 0 || v == 3;
                buffer.addVertex(submit.pose(), (float)p[0], (float)(top ? b.maxY : b.minY), (float)p[1])
                    .setUv(v < 2 ? sprite.getU0() : sprite.getU1(), top ? sprite.getV0() : sprite.getV1())
                    .setColor(seed).setLight(15728880);
            }
        }
    }
}
