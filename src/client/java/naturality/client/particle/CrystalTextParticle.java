package naturality.client.particle;
import java.util.IdentityHashMap;
import naturality.NaturalityParticles;
import naturality.client.entity.CrystalVisuals;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;
public final class CrystalTextParticle extends SingleQuadParticle {
    private static @org.jspecify.annotations.Nullable FabricSpriteSet sprites;
    private static final IdentityHashMap<EndCrystal,CrystalTextParticle> ACTIVE=new IdentityHashMap<>();
    private final EndCrystal owner;
    private Vec3 shake=Vec3.ZERO,previousShake=Vec3.ZERO;
    private CrystalTextParticle(ClientLevel level,EndCrystal owner,FabricSpriteSet sprites) {
        super(level,owner.getX(),owner.getY()+CrystalVisuals.centerHeight(owner.time),owner.getZ(),sprites.first());
        this.owner=owner;quadSize=.255F;hasPhysics=false;
    }
    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(NaturalityParticles.END_CRYSTAL_TEXT,set -> {
            sprites=set;return (_,_,_,_,_,_,_,_,_) -> null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var level = client.level;
            var currentSprites = sprites;
            ACTIVE.entrySet().removeIf(entry -> {
                if(level==null || entry.getKey().level()!=level || entry.getKey().isRemoved()) {entry.getValue().remove();return true;}
                return false;
            });
            if(level==null || client.isPaused() || currentSprites==null) return;
            for(var e:level.entitiesForRendering()) if(e instanceof EndCrystal crystal) {
                var particle=ACTIVE.get(crystal);
                if(particle==null || !particle.isAlive()) {
                    particle=new CrystalTextParticle(level,crystal,currentSprites);ACTIVE.put(crystal,particle);client.particleEngine.add(particle);
                }
            }
        });
    }
    public void tick() {
        if(owner.isRemoved()) {remove();return;}
        age++;xo=x;yo=y;zo=z;previousShake=shake;
        Vec3 next=new Vec3((random.nextDouble()-.5)*.048,(random.nextDouble()-.5)*.048,(random.nextDouble()-.5)*.048);
        shake=shake.lerp(next,.6);
        setPos(owner.getX()+shake.x,owner.getY()+CrystalVisuals.centerHeight(owner.time)+shake.y,owner.getZ()+shake.z);
        var currentSprites = sprites;
        if (currentSprites == null) { remove(); return; }
        var frames=currentSprites.getSprites();
        if (frames.isEmpty()) { remove(); return; }
        setSprite(frames.get(((int)(age/2.5))%frames.size()));
    }
    public void extract(QuadParticleRenderState state,Camera camera,float partial) {
        Vec3 position=owner.getPosition(partial).add(0,CrystalVisuals.centerHeight(owner.time+partial),0).add(previousShake.lerp(shake,partial));
        xo=x=position.x;yo=y=position.y;zo=z=position.z;
        super.extract(state,camera,partial);
    }
    protected int getLightCoords(float partial) {return 15728880;}
    protected Layer getLayer() {return Layer.TRANSLUCENT;}
}


