package naturality.client.mixin;

import naturality.client.particle.WaterParticleTint;
import naturality.config.NaturalityConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(SingleQuadParticle.class)
public abstract class WaterParticleTintMixin extends Particle implements WaterParticleTint.Access {
    @Unique private boolean naturality$water;
    @Unique private boolean naturality$rain;
    @Override public void naturality$rainParticle(boolean rain) {naturality$rain=rain;}
    protected WaterParticleTintMixin(ClientLevel level,double x,double y,double z) {super(level,x,y,z);}
    @Override public void naturality$waterParticle(boolean water) {naturality$water=water;}
    @Override public boolean naturality$isWaterParticle() {return naturality$water;}
    @ModifyArgs(method="extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;add(Lnet/minecraft/client/particle/SingleQuadParticle$Layer;FFFFFFFFFFFFII)V"))
    private void naturality$tint(Args args) {
        if(!naturality$water || !NaturalityConfig.get().liquids.water || !NaturalityConfig.get().liquids.waterParticleTint) return;
        SingleQuadParticle.Layer layer=args.get(0);
        args.set(0,naturality$rain ? naturality.client.particle.RainParticleTint.TRANSPARENT
            : layer.translucent()?WaterParticleTint.TRANSPARENT:WaterParticleTint.SOLID);
        int original=args.get(13);
        int tint=BiomeColors.getAverageWaterColor(level,BlockPos.containing(x,y,z));
        // Replace the baked blue vertex tint; keep lifetime opacity and vanilla lightmap.
        args.set(13,(original & 0xff000000) | (tint & 0xffffff));
    }
}
