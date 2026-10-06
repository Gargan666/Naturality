package naturality.client.particle;

import java.util.Optional;
import naturality.NaturalityParticles;
import naturality.client.weather.ParticleWeather;
import naturality.client.weather.WeatherParticleContext;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** One velocity-aligned card contains many drops/flakes; no per-drop simulation. */
public final class WeatherClusterParticle extends SingleQuadParticle {
    private static final ParticleLimit LIMIT = new ParticleLimit(1600);
    private static final Optional<ParticleLimit> OPTIONAL_LIMIT = Optional.of(LIMIT);
    private final boolean snow;
    private final boolean flip;
    private final double fallSpeed;
    private final @org.jspecify.annotations.Nullable WeatherSway sway;
    private final org.joml.Matrix3f orientationBasis=new org.joml.Matrix3f();
    private final Quaternionf orientationRotation=new Quaternionf();
    private final BlockPos.MutableBlockPos queryPos=new BlockPos.MutableBlockPos();
    private long lastTickTime;
    private float opacity;
    private float snowFallMultiplier=1;
    private boolean boundsDirty;
    public static void initialize() {
        var registry = ParticleProviderRegistry.getInstance();
        registry.register(NaturalityParticles.RAIN_CLUSTER, sprites ->
            (_, level, x,y,z,_,_,_,random) -> {
                var textures = sprites.getSprites();
                var weather=WeatherParticleContext.state(level);
                boolean heavy = textures.size()>=6 && random.nextFloat()<weather.heavyParticleChance(x,y,z);
                int first = heavy ? 3 : 0;
                return new WeatherClusterParticle(level,x,y,z,
                    textures.get(first + random.nextInt(3)),random,false);
            });
        registry.register(NaturalityParticles.SNOW_CLUSTER, sprites ->
            (_, level, x,y,z,_,_,_,random) -> {
                var textures=sprites.getSprites();
                var weather=WeatherParticleContext.state(level);
                boolean heavy=textures.size()>=6 && random.nextFloat()<weather.heavyParticleChance(x,y,z);
                return new WeatherClusterParticle(level,x,y,z,textures.get((heavy?3:0)+random.nextInt(3)),random,true);
            });
    }
    private WeatherClusterParticle(ClientLevel level, double x, double y, double z,
            TextureAtlasSprite sprite, RandomSource random, boolean snow) {
        super(level,x,y,z,sprite);
        this.snow = snow;
        flip = random.nextBoolean();
        double phase=random.nextDouble()*Math.PI*2;
        sway=snow?new WeatherSway(phase):null;

        fallSpeed = snow ? .055 + random.nextDouble() * .035 : .6 + random.nextDouble() * .2;
        quadSize = 1.8F + random.nextFloat() * .5F;
        lifetime = snow ? 240 : 70;

        hasPhysics = false;
        alpha = 0;
        lastTickTime = level.getGameTime();
        updateVelocity(WeatherParticleContext.state(level),true);
        updateTint();
    }
    public boolean snow() { return snow; }
    public long lastTickTime() { return lastTickTime; }
    public Vec3 velocity() { return new Vec3(xd,yd,zd); }
    // These cards do not collide, and vanilla frustum tests use their position.
    // Build the immutable box only when a caller actually asks for it.
    @Override public void setPos(double x,double y,double z) {
        if(hasPhysics) {super.setPos(x,y,z);return;}
        this.x=x;this.y=y;this.z=z;boundsDirty=true;
    }
    @Override public net.minecraft.world.phys.AABB getBoundingBox() {
        if(boundsDirty)super.setPos(x,y,z);
        return super.getBoundingBox();
    }
    @Override public void setBoundingBox(net.minecraft.world.phys.AABB box) {
        super.setBoundingBox(box);boundsDirty=false;
    }
    public static Quaternionf orientation(Vec3 velocity, Vec3 toCamera) {
        // Keep local Y exactly parallel to velocity; only rotate around that axis
        // to face the observer's position as closely as the constraint permits.
        return WeatherCardGeometry.orientation(velocity.x,velocity.y,velocity.z,toCamera.x,toCamera.y,toCamera.z,
            new org.joml.Matrix3f(),new Quaternionf());
    }
    private void updateTint() {
        if (snow) return;
        int color = WeatherParticleContext.waterTint(level, queryPos.set(x,y,z));
        rCol = ((color >> 16) & 255) / 255F;
        gCol = ((color >> 8) & 255) / 255F;
        bCol = (color & 255) / 255F;
    }
    @Override protected float getU0() { return flip ? super.getU1() : super.getU0(); }
    @Override protected float getU1() { return flip ? super.getU0() : super.getU1(); }
    private void updateVelocity(WeatherParticleContext.State weather,boolean initial) {
        double blend=initial?1:.15;
        if(snow) {
            var oscillation=java.util.Objects.requireNonNull(sway);
            if(!initial)oscillation.tick();
            xd+=(weather.snowWindX()+oscillation.x()*.025-xd)*blend;
            zd+=(weather.snowWindZ()+oscillation.z()*.025-zd)*blend;
            snowFallMultiplier+=(weather.snowFallMultiplier()-snowFallMultiplier)*(initial?1:.15F);
            yd=-fallSpeed*snowFallMultiplier*(1+.12*oscillation.y());
        } else {
            xd+=(weather.rainWindX()-xd)*blend;zd+=(weather.rainWindZ()-zd)*blend;
            yd=-fallSpeed;
        }
    }
    @Override public void tick() {
        lastTickTime = level.getGameTime();
        xo=x; yo=y; zo=z; oRoll=roll;
        if (++age >= lifetime) { remove(); return; }
        var weather = WeatherParticleContext.state(level);
        if (!weather.enabled()) { remove(); return; }
        var kind = WeatherParticleContext.precipitation(level, queryPos.set(x,y,z));
        if (kind != (snow ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN)) { remove(); return; }
        updateVelocity(weather,false);
        double nx=x+xd, ny=y+yd, nz=z+zd;
        // The weather card may visually overlap the ground while its center is
        // still falling. Remove it only once the center reaches the surface.
        double clearance = ParticleWeather.clearance(level,nx,ny,nz,0);
        if (clearance <= 0) { remove(); return; }
        var eye = weather.eye();
        double dx=nx-eye.x, dy=ny-eye.y, dz=nz-eye.z;
        double horizontalSquared=dx*dx+dz*dz;
        int radius=weather.radius();
        // Preserve the existing camera-near weather volume, independent of facing/occlusion.
        if(horizontalSquared>(radius+2)*(radius+2) || ny<eye.y-8 || ny>eye.y+16) {remove();return;}
        double distanceSquared=horizontalSquared+dy*dy;
        float nearFade=distanceSquared>=9?1:Math.clamp((float)(Math.sqrt(distanceSquared)-1)/2,0,1);
        float edgeFade=radius>=2 && horizontalSquared<=(radius-2)*(radius-2)?1
            :Math.clamp((float)(radius+1-Math.sqrt(horizontalSquared))/3,0,1);
        setPos(nx,ny,nz);
        updateTint();

        opacity = (snow ? .85F : .7F) * Math.min(1, age/4F) * Math.min(1,(lifetime-age)/8F)
            * Math.clamp((float)clearance,0,1) * nearFade * edgeFade;
    }
    @Override public void extract(QuadParticleRenderState output, Camera camera, float partial) {
        var eye=camera.position();
        double px=Mth.lerp(partial,xo,x)-eye.x,py=Mth.lerp(partial,yo,y)-eye.y,pz=Mth.lerp(partial,zo,z)-eye.z;
        var rotation=WeatherCardGeometry.orientation(xd,yd,zd,-px,-py,-pz,orientationBasis,orientationRotation);
        alpha=opacity;
        extractRotatedQuad(output,rotation,(float)px,(float)py,(float)pz,partial);
    }
    @Override protected int getLightCoords(float partial) {
        return WeatherParticleContext.light(level,queryPos.set(x,y,z));
    }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    @Override public Optional<ParticleLimit> getParticleLimit() { return OPTIONAL_LIMIT; }
}

