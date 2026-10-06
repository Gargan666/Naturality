package naturality.client.mixin;

import naturality.client.particle.WaterParticleTint;
import naturality.config.NaturalityConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.client.weather.WeatherParticleContext;

@Mixin(SingleQuadParticle.class)
public abstract class WaterParticleTintMixin extends Particle implements WaterParticleTint.Access {
    @Unique private boolean naturality$water;
    @Unique private boolean naturality$rain;
    @Override public void naturality$rainParticle(boolean rain) {naturality$rain=rain;}
    protected WaterParticleTintMixin(ClientLevel level,double x,double y,double z) {super(level,x,y,z);}
    @Override public void naturality$waterParticle(boolean water) {naturality$water=water;}
    @Override public boolean naturality$isWaterParticle() {return naturality$water;}
    @ModifyExpressionValue(method="extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/particle/SingleQuadParticle;getLayer()Lnet/minecraft/client/particle/SingleQuadParticle$Layer;"))
    private SingleQuadParticle.Layer naturality$tintLayer(SingleQuadParticle.Layer layer) {
        if(!naturality$water || !NaturalityConfig.get().liquids.water || !NaturalityConfig.get().liquids.waterParticleTint)return layer;
        return naturality$rain ? naturality.client.particle.RainParticleTint.TRANSPARENT
            : layer.translucent()?WaterParticleTint.TRANSPARENT:WaterParticleTint.SOLID;
    }
    @ModifyExpressionValue(method="extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"))
    private int naturality$tintColor(int original) {
        if(!naturality$water || !NaturalityConfig.get().liquids.water || !NaturalityConfig.get().liquids.waterParticleTint)return original;
        int tint=WeatherParticleContext.waterTint(level,BlockPos.containing(x,y,z));
        return (original & 0xff000000) | (tint & 0xffffff);
    }
}
