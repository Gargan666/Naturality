package naturality.client.particle;

import naturality.NaturalityParticles;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.RandomSource;

/** Short upward/outward splash plumes using the supplied small and large sprite sequences. */
public final class LavafallParticle extends SingleQuadParticle implements BorderSplashParticle {
    private final FabricSpriteSet sprites;
    private final float initialSize;
    private float borderSizeMultiplier=1;
    private int warmFadeTicks;
    @Override public void naturality$enlargeAtRestingEdge(float fallSize){borderSizeMultiplier=2*fallSize;}
    @Override public int naturality$lifetime(){return lifetime;}
    @Override public void naturality$warmStart(int elapsedTicks) {
        for(int i=0;i<Math.min(elapsedTicks,lifetime-2);i++)tick();
        warmFadeTicks=4;
        alpha=0;
    }
    public static void initialize() {
        var registry=ParticleProviderRegistry.getInstance();
        registry.register(NaturalityParticles.LAVAFALL,sprites->
            (_,level,x,y,z,vx,vy,vz,random)->new LavafallParticle(level,x,y,z,vx,vy,vz,sprites,random,false));
        registry.register(NaturalityParticles.LAVAFALL_BIG,sprites->
            (_,level,x,y,z,vx,vy,vz,random)->new LavafallParticle(level,x,y,z,vx,vy,vz,sprites,random,true));
    }
    private LavafallParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,
                              FabricSpriteSet sprites,RandomSource random,boolean big) {
        super(level,x,y,z,sprites.first());
        this.sprites=sprites;
        lifetime=Math.max(1,sprites.getSprites().size())*4;
        initialSize=(0.15F+random.nextFloat()*0.05F)*(big?2.5F:1);
        quadSize=initialSize;
        xd=vx; yd=vy+(big?0.09:0.055)+random.nextDouble()*0.03; zd=vz;
        setSize(0.08F,0.08F);
        alpha=0.8F;
    }
    @Override public void tick() {
        xo=x;yo=y;zo=z;
        if(++age>=lifetime) {remove();return;}
        move(xd,yd,zd);
        xd*=0.96;zd*=0.96;yd=yd*0.96-0.004;
        var frames=sprites.getSprites();
        if(!frames.isEmpty())setSprite(frames.get(Math.min(frames.size()-1,age/4)));
        if(warmFadeTicks>0)alpha=0.8F*(5-warmFadeTicks--)/4;
    }
    @Override public float getQuadSize(float partial) {return initialSize*borderSizeMultiplier;}
    @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
}


