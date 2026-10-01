package naturality.client.particle;

import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/** Separate shaders neutralize only water sprites, including resource-pack replacements. */
public final class WaterParticleTint {
    private static final RenderPipeline.Snippet EFFECT=RenderPipeline.builder()
        .withFragmentShader(Naturality.id("core/water_particle")).buildSnippet();
    private static final RenderPipeline OPAQUE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
        .withLocation(Naturality.id("pipeline/water_particle_opaque")).withColorTargetState(ColorTargetState.DEFAULT).build());
    private static final RenderPipeline TRANSLUCENT=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET,EFFECT)
        .withLocation(Naturality.id("pipeline/water_particle_translucent"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_water_particle",
        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET,EFFECT)).build());
    public static final SingleQuadParticle.Layer SOLID=new SingleQuadParticle.Layer(false,naturality.client.AtlasLocations.PARTICLES,OPAQUE);
    public static final SingleQuadParticle.Layer TRANSPARENT=new SingleQuadParticle.Layer(true,naturality.client.AtlasLocations.PARTICLES,TRANSLUCENT,OIT);
    public static void initialize() {}
    public static boolean includes(ParticleType<?> type) {
        return type==ParticleTypes.DRIPPING_WATER || type==ParticleTypes.FALLING_WATER
            || type==ParticleTypes.DRIPPING_DRIPSTONE_WATER || type==ParticleTypes.FALLING_DRIPSTONE_WATER
            || type==ParticleTypes.RAIN || type==ParticleTypes.SPLASH || type==ParticleTypes.FISHING
            || type==ParticleTypes.BUBBLE || type==ParticleTypes.BUBBLE_POP
            || type==ParticleTypes.BUBBLE_COLUMN_UP || type==ParticleTypes.CURRENT_DOWN
            || type==ParticleTypes.UNDERWATER;
    }
    public interface Access {
        default void naturality$rainParticle(boolean rain) {}
        void naturality$waterParticle(boolean water);
        boolean naturality$isWaterParticle();
    }
}

