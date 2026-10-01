package naturality.client.particle;

import java.util.Optional;
import naturality.NaturalityParticles;
import naturality.client.weather.ParticleWeather;
import naturality.weather.WeatherSystem;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
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
    private final boolean snow;
    private final boolean flip;
    private final double phase, fallSpeed;
    private long lastTickTime;
    private float opacity;
    public static void initialize() {
        var registry = ParticleProviderRegistry.getInstance();
        registry.register(NaturalityParticles.RAIN_CLUSTER, sprites ->
            (_, level, x,y,z,_,_,_,random) -> new WeatherClusterParticle(level,x,y,z,sprites.getSprites().get(random.nextInt(sprites.getSprites().size())),random,false));
        registry.register(NaturalityParticles.SNOW_CLUSTER, sprites ->
            (_, level, x,y,z,_,_,_,random) -> new WeatherClusterParticle(level,x,y,z,sprites.getSprites().get(random.nextInt(sprites.getSprites().size())),random,true));
    }
    private WeatherClusterParticle(ClientLevel level, double x, double y, double z,
            TextureAtlasSprite sprite, RandomSource random, boolean snow) {
        super(level,x,y,z,sprite);
        this.snow = snow;
        flip = random.nextBoolean();
        phase = random.nextDouble() * Math.PI * 2;

        fallSpeed = snow ? .055 + random.nextDouble() * .035 : .6 + random.nextDouble() * .2;
        quadSize = 1.8F + random.nextFloat() * .5F;
        lifetime = snow ? 240 : 70;

        hasPhysics = false;
        alpha = 0;
        lastTickTime = level.getGameTime();
        yd = -fallSpeed;
        updateTint();
    }
    public boolean snow() { return snow; }
    public long lastTickTime() { return lastTickTime; }
    public Vec3 velocity() { return new Vec3(xd,yd,zd); }
    public static Quaternionf orientation(Vec3 velocity, Vec3 toCamera) {
        // Keep local Y exactly parallel to velocity; only rotate around that axis
        // to face the observer's position as closely as the constraint permits.
        return PortalMoteGeometry.orientation(velocity, toCamera).rotateZ(-(float)Math.PI / 2);
    }
    private void updateTint() {
        if (snow) return;
        int color = BiomeColors.getAverageWaterColor(level, BlockPos.containing(x,y,z));
        rCol = ((color >> 16) & 255) / 255F;
        gCol = ((color >> 8) & 255) / 255F;
        bCol = (color & 255) / 255F;
    }
    @Override protected float getU0() { return flip ? super.getU1() : super.getU0(); }
    @Override protected float getU1() { return flip ? super.getU0() : super.getU1(); }
    @Override public void tick() {
        lastTickTime = level.getGameTime();
        xo=x; yo=y; zo=z; oRoll=roll;
        var weather = WeatherSystem.state(level);
        var client = Minecraft.getInstance();
        if (++age >= lifetime || client.level != level || !ParticleWeather.enabled(level)
                || weather == null || weather.rain() <= 0) { remove(); return; }
        var kind = level.getPrecipitationAt(BlockPos.containing(x,y,z));
        if (kind != (snow ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN)) { remove(); return; }
        double time = phase + age * .065;
        double targetX = weather.windX() * (snow ? .10 : .34) + (snow ? Math.sin(time) * .025 : 0);
        double targetZ = weather.windZ() * (snow ? .10 : .34) + (snow ? Math.cos(time*.81) * .025 : 0);
        xd += (targetX-xd)*.15; zd += (targetZ-zd)*.15;
        yd = -fallSpeed * (snow ? 1 + .12 * Math.sin(time*.7) : 1);
        double nx=x+xd, ny=y+yd, nz=z+zd;
        // The weather card may visually overlap the ground while its center is
        // still falling. Remove it only once the center reaches the surface.
        double clearance = ParticleWeather.clearance(level,nx,ny,nz,0);
        if (clearance <= 0) { remove(); return; }
        var eye = client.gameRenderer.mainCamera().position();
        double distance = eye.distanceTo(new Vec3(nx,ny,nz));
        double horizontal = Math.hypot(nx-eye.x,nz-eye.z);
        int radius = ParticleWeather.radius(client);
        // Do not let long-lived snow cards outside the visible volume monopolize the budget.
        if (horizontal > radius+2 || ny < eye.y-8 || ny > eye.y+16) { remove(); return; }
        setPos(nx,ny,nz);
        updateTint();

        float nearFade = Math.clamp((float)(distance-1)/2,0,1);
        float edgeFade = Math.clamp((float)(radius+1-horizontal)/3,0,1);
        opacity = (snow ? .85F : .7F) * Math.min(1, age/4F) * Math.min(1,(lifetime-age)/8F)
            * Math.clamp((float)clearance,0,1) * nearFade * edgeFade;
    }
    @Override public void extract(QuadParticleRenderState output, Camera camera, float partial) {
        Vec3 position = new Vec3(Mth.lerp(partial,xo,x),Mth.lerp(partial,yo,y),Mth.lerp(partial,zo,z));
        var rotation = orientation(velocity(),camera.position().subtract(position));

        alpha = opacity;
        extractRotatedQuad(output,camera,rotation,partial);
    }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    @Override public Optional<ParticleLimit> getParticleLimit() { return Optional.of(LIMIT); }
}

