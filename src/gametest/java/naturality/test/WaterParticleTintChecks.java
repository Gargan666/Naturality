package naturality.test;

import naturality.client.particle.WaterParticleTint;
import naturality.config.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

final class WaterParticleTintChecks {
    static void check(Minecraft client) {
        var types=new net.minecraft.core.particles.SimpleParticleType[]{
            ParticleTypes.DRIPPING_WATER,ParticleTypes.FALLING_WATER,ParticleTypes.DRIPPING_DRIPSTONE_WATER,
            ParticleTypes.FALLING_DRIPSTONE_WATER,ParticleTypes.RAIN,ParticleTypes.SPLASH,ParticleTypes.FISHING,
            ParticleTypes.BUBBLE,ParticleTypes.BUBBLE_POP,ParticleTypes.BUBBLE_COLUMN_UP,ParticleTypes.CURRENT_DOWN,ParticleTypes.UNDERWATER};
        for(var type:types) {
            var particle=(SingleQuadParticle)client.particleEngine.createParticle(type,0,101,0,0,0,0);
            if(particle==null || !((WaterParticleTint.Access)particle).naturality$isWaterParticle())
                throw new AssertionError("Water provider was not marked: "+type);
            var capture=new Capture();
            particle.extract(capture,client.gameRenderer.mainCamera(),0);
            int expected=BiomeColors.getAverageWaterColor(client.level,new BlockPos(0,101,0)) & 0xffffff;
            if((capture.color & 0xffffff)!=expected || !capture.waterLayer())
                throw new AssertionError("Incorrect water tint or grayscale layer: "+type);
            particle.move(8,0,0);
            particle.extract(capture,client.gameRenderer.mainCamera(),0);
            expected=BiomeColors.getAverageWaterColor(client.level,new BlockPos(8,101,0)) & 0xffffff;
            if((capture.color & 0xffffff)!=expected) throw new AssertionError("Tint did not follow movement");
            boolean originalTint = NaturalityConfig.get().liquids.waterParticleTint;
            NaturalityConfig.get().liquids.waterParticleTint=false;
            try {
                particle.extract(capture,client.gameRenderer.mainCamera(),0);
                if(capture.waterLayer()) throw new AssertionError("Disabled particle tint must use the vanilla layer independently of water rendering");
            } finally {NaturalityConfig.get().liquids.waterParticleTint=originalTint;}
            NaturalityConfig.get().liquids.water=false;
            try {
                particle.extract(capture,client.gameRenderer.mainCamera(),0);
                if(capture.waterLayer()) throw new AssertionError("Disabled water must use vanilla layer");
            } finally {NaturalityConfig.get().liquids.water=true;}
        }
        for(var type:new net.minecraft.core.particles.SimpleParticleType[]{ParticleTypes.DRIPPING_LAVA,ParticleTypes.DRIPPING_HONEY,ParticleTypes.SMOKE,naturality.NaturalityParticles.WATERFALL,naturality.NaturalityParticles.WATERFALL_BIG}) {
            var p=client.particleEngine.createParticle(type,0,101,0,0,0,0);
            if(((WaterParticleTint.Access)p).naturality$isWaterParticle()) throw new AssertionError("Nonwater particle was tinted");
        }
    }
    private static final class Capture extends QuadParticleRenderState {
        int color; SingleQuadParticle.Layer layer;
        boolean waterLayer(){return layer==WaterParticleTint.SOLID || layer==WaterParticleTint.TRANSPARENT || layer==naturality.client.particle.RainParticleTint.TRANSPARENT;}
        @Override public void add(SingleQuadParticle.Layer layer,float x,float y,float z,float rx,float ry,float rz,float rw,
                                  float scale,float u0,float u1,float v0,float v1,int color,int light) {
            this.layer=layer;this.color=color;
        }
    }
}


