package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.fluid.WaterSurface;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla entry bursts assume a full block; use the rendered surface at each random spawn point. */
@Mixin(Entity.class)
public abstract class WaterEntryParticleHeightMixin {
    @WrapOperation(method="doWaterSplashEffect",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void naturality$surfaceHeight(Level level,ParticleOptions type,double x,double y,double z,
            double vx,double vy,double vz,Operation<Void> original) {
        if(level instanceof ClientLevel client && naturality.config.NaturalityConfig.get().liquids.water) {
            var pos=BlockPos.containing(x,y-1,z);
            for(int offset=0;offset>=-1;offset--) {
                var surface=WaterSurface.at(client,pos.above(offset));
                if(surface==null || !surface.contains(x-surface.pos().getX(),z-surface.pos().getZ()))continue;
                y=surface.height(x-surface.pos().getX(),z-surface.pos().getZ())
                    +(type.getType()==ParticleTypes.BUBBLE?-0.02:0.01);
                break;
            }
        }
        original.call(level,type,x,y,z,vx,vy,vz);
    }
}
