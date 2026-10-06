package naturality.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.texture.OverlayTexture;
import naturality.Naturality;

/** The authored cube UV net: square texels on all six faces, with whole-pixel scrolling. */
public final class FireballShell {
    private static final RenderType LAYER = RenderType.create("naturality_fireball_shell",
        RenderSetup.builder(RenderPipelines.register(
            com.mojang.renderpearl.api.pipeline.RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                .withLocation(Naturality.id("pipeline/fireball_shell"))
                .withShaderDefine("ALPHA_CUTOUT", .1F)
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withCull(false)
                .withColorTargetState(com.mojang.renderpearl.api.pipeline.ColorTargetState.DEFAULT)
                .build()))
            .withTexture("Sampler0", Naturality.id("textures/entity/fireball_fire.png"),
                () -> RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT,
                    FilterMode.NEAREST, FilterMode.NEAREST, false))
            .createRenderSetup());
    // Coordinates in model pixels. Each face lists its outward-facing corners.
    public static final int[][][] FACES = {
        {{-5,-5,-5},{-5,5,-5},{5,5,-5},{5,-5,-5}},
        {{5,-5,5},{5,5,5},{-5,5,5},{-5,-5,5}},
        {{-5,-5,5},{-5,5,5},{-5,5,-5},{-5,-5,-5}},
        {{5,-5,-5},{5,5,-5},{5,5,5},{5,-5,5}},
        {{-5,-5,5},{-5,-5,-5},{5,-5,-5},{5,-5,5}},
        {{-5,5,-5},{-5,5,5},{5,5,5},{5,5,-5}}
    };
    private static final int[][] NORMALS = {{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
    // Original shell atlas net, translated from (0,44) to the separate texture's (0,0).
    // Corners correspond to FACES, preserving the model's face orientation.
    private static final int[][][] UV = {
        {{10,10},{10,20},{20,20},{20,10}},
        {{30,10},{30,20},{40,20},{40,10}},
        {{0,10},{0,20},{10,20},{10,10}},
        {{20,10},{20,20},{30,20},{30,10}},
        {{10,0},{10,10},{20,10},{20,0}},
        {{20,10},{20,0},{30,0},{30,10}}
    };

    private FireballShell() { }

    public static float u(int face, int corner, int step) { return (UV[face][corner][0] + step) / 44.0F; }
    public static float v(int face, int corner, int step) { return (UV[face][corner][1] + step) / 21.0F; }

    public static void submit(PoseStack pose, SubmitNodeCollector collector, float age) {
        int step = Math.floorMod((int)Math.floor(age / 2), 924);
        collector.submitCustomGeometry(pose, LAYER, (submitted, buffer) -> {
                for (int f = 0; f < FACES.length; f++) {
                    int[] normal = NORMALS[f];
                    for (int corner = 0; corner < 4; corner++) {
                        int[] p = FACES[f][corner];
                        buffer.addVertex(submitted, p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F)
                            .setColor(-1).setUv(u(f, corner, step), v(f, corner, step))
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880)
                            .setNormal(submitted, normal[0], normal[1], normal[2]);
                    }
                }
            });
    }
}

