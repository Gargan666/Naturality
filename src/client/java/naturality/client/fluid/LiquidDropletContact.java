package naturality.client.fluid;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;

/** Opposite liquid quenches the droplet; it never produces an impact ring. */
public final class LiquidDropletContact {
    public static boolean impact(ClientLevel level,boolean lava,double x0,double y0,double z0,double x,double y,double z) {
        return impact(level,lava,x0,y0,z0,x,y,z,true);
    }
    public static boolean impact(ClientLevel level,boolean lava,double x0,double y0,double z0,double x,double y,double z,boolean ring) {
        boolean opposite=lava?WaterRipples.impact(level,x0,y0,z0,x,y,z,false):LavaRipples.impact(level,x0,y0,z0,x,y,z,false);
        if(opposite) {
            var smoke=net.minecraft.client.Minecraft.getInstance().particleEngine.createParticle(ParticleTypes.SMOKE,x,y+.04,z,0,.015,0);
            if(smoke!=null)smoke.scale(.35F);
            return true;
        }
        return lava?LavaRipples.impact(level,x0,y0,z0,x,y,z,ring):WaterRipples.impact(level,x0,y0,z0,x,y,z,ring);
    }
}

