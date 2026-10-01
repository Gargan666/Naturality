package naturality.client.particle;

import naturality.NaturalityParticles;
import naturality.client.fluid.*;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;

/** Heavy molten droplets live until contact, never inheriting water's baked blue. */
public final class LavaSplashParticle extends SingleQuadParticle implements ImpactDroplet {
    private boolean impactRing=true;
    @Override public void naturality$impactRing(boolean enabled){impactRing=enabled;}
    public static void initialize(){ParticleProviderRegistry.getInstance().register(NaturalityParticles.LAVA_SPLASH,sprites->
        (_,level,x,y,z,vx,vy,vz,_)->new LavaSplashParticle(level,x,y,z,vx,vy,vz,sprites.first()));}
    private LavaSplashParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,TextureAtlasSprite sprite) {
        super(level,x,y,z,sprite);setSize(.03F,.03F);quadSize=.06F;
        xd=vx;yd=vy;zd=vz;
        int tint=LavaEffects.brightColor();setColor(ARGB.red(tint)/255F,ARGB.green(tint)/255F,ARGB.blue(tint)/255F);
    }
    @Override public void naturality$landBeforeExpiring(){}
    @Override public void tick() {
        xo=x;yo=y;zo=z;yd-=.025;move(xd,yd,zd);xd*=.98;yd*=.98;zd*=.98;
        if(LiquidDropletContact.impact(level,true,xo,yo,zo,x,y,z,impactRing) || onGround || y<level.getMinY()-16)remove();
        var pos=BlockPos.containing(x,y,z);var fluid=level.getFluidState(pos);
        if((fluid.is(FluidTags.WATER)||fluid.is(FluidTags.LAVA)) && yd<0 && y<pos.getY()+fluid.getHeight(level,pos))remove();
    }
    @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
}


