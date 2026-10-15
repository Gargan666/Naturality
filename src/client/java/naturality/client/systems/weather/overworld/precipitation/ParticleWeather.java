package naturality.client.systems.weather.overworld.precipitation;

import naturality.client.systems.weather.framework.WeatherClient.WeatherParticleContext;
import java.util.ArrayList;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import naturality.config.live.NaturalityConfig;
import naturality.systems.weather.framework.WeatherSystem;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import naturality.client.api.geometry.ParticleGeometry;
import java.util.Optional;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Bounded, camera-near spawning; existing cards always move in world coordinates. */
@org.jspecify.annotations.NullMarked
public final class ParticleWeather {
    public static final int MAX_CLUSTERS = 1536;
    private static final double MAX_CARD_EXTENT = 3.4;
    private static final ArrayList<WeatherClusterParticle> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private ParticleWeather() {}
    public static void initialize() {
        WeatherClusterParticle.initialize();
        naturality.client.systems.sleep.SleepVisuals.register(ParticleWeather::tick);
    }
    public static boolean enabled(ClientLevel level) { return naturality.config.live.NaturalityConfig.get().effects.weatherParticles && level.canHaveWeather() && weatherState(level) != null; }
    public static naturality.systems.weather.framework.WeatherSystem.@org.jspecify.annotations.Nullable WeatherState weatherState(ClientLevel level) {
        var state = WeatherSystem.state(level);
        return state == null && naturality.client.ui.menus.previews.MenuPreview.active()
                ? new naturality.systems.weather.framework.WeatherSystem.WeatherState(level.getRainLevel(1) * 25, 0, 50, 0) : state;
    }
    public static int activeCount() { return ACTIVE.size(); }
    public static long snowCount() { return ACTIVE.stream().filter(WeatherClusterParticle::snow).count(); }
    public static void clear() { for (var p : ACTIVE) p.remove(); ACTIVE.clear(); }
    public static int radius(Minecraft client) { return Math.clamp(Math.round(client.options.weatherRadius().get() * 1.5F), 0, 36); }
    private static void tick(Minecraft client) {
        var level = client.level;
        if (level == null) { clear(); world = null; return; }
        WeatherParticleContext.begin(level);
        try { tickParticles(client); }
        finally { WeatherParticleContext.end(); }
    }
    private static void tickParticles(Minecraft client) {
        if (world != client.level) { clear(); world = client.level; }
        var world = ParticleWeather.world;
        if (world == null || !enabled(world)) { clear(); return; }
        if (client.isPaused()) return;
        if (naturality.client.ui.menus.previews.MenuPreview.active()) { tickPreview(client); return; }
        ACTIVE.removeIf(p -> {
            // Engine eviction/resource reload may stop ticking a still-alive object.
            if (world.getGameTime() - p.lastTickTime() > 10) p.remove();
            return !p.isAlive();
        });
        int radius = radius(client);
        var state = WeatherSystem.state(world);
        if (state == null) { clear(); return; }
        float quality = switch (client.options.particles().get()) {
            case MINIMAL -> .25F;
            case DECREASED -> .5F;
            default -> 1F;
        };
        int target = Math.round(Math.min(MAX_CLUSTERS, 80 * state.precipitationDensity() * radius * radius / 256F) * quality);
        while (ACTIVE.size() > target) ACTIVE.removeLast().remove();
        if(ACTIVE.size()>=target)return;
        var eye = client.gameRenderer.mainCamera().position();
        double cloudTop = highestActiveCloudTop(client);
        int births = 0;
        for (int attempts = 0; attempts < 320 && births < 80 && ACTIVE.size() < target; attempts++) {
            double x = eye.x + (RANDOM.nextDouble() * 2 - 1) * radius;
            double z = eye.z + (RANDOM.nextDouble() * 2 - 1) * radius;
            if ((x-eye.x)*(x-eye.x)+(z-eye.z)*(z-eye.z) > radius*radius) continue;
            double y = eye.y - 4 + RANDOM.nextDouble() * 18;
            if (y + MAX_CARD_EXTENT > cloudTop) continue;
            var pos = BlockPos.containing(x, y, z);
            if (!naturality.api.worlds.LoadedChunks.has(world, pos)) continue;
            var kind = WeatherParticleContext.precipitation(world, pos);
            if (kind == Biome.Precipitation.NONE || clearance(world, x, y, z, MAX_CARD_EXTENT) <= 0) continue;
            var particle = client.particleEngine.createParticle(kind == Biome.Precipitation.SNOW
                ? NaturalityParticles.SNOW_CLUSTER : NaturalityParticles.RAIN_CLUSTER,
                x, y, z, 0, 0, 0);
            if (particle instanceof WeatherClusterParticle cluster) { ACTIVE.add(cluster); births++; }
        }
    }
    /** A bounded foreground sheet: the orbit eye is often outside loaded terrain. */
    private static void tickPreview(Minecraft client) {
        var level = client.level;
        if (level == null) return;
        var climate = weatherState(level);
        var kind = WeatherParticleContext.precipitation(level, naturality.client.ui.menus.previews.MenuPreview.spawn());
        ACTIVE.removeIf(p -> !p.isAlive() || p.snow() != (kind == Biome.Precipitation.SNOW) || !previewContains(p.position()));
        float quality = switch (client.options.particles().get()) {
            case MINIMAL -> .25F; case DECREASED -> .5F; default -> 1F;
        };
        int target = climate == null || kind == Biome.Precipitation.NONE ? 0
                : Math.round(Math.min(256, climate.precipitationDensity() * 100) * quality);
        while (ACTIVE.size() > target) ACTIVE.removeLast().remove();
        var camera = client.gameRenderer.mainCamera();
        var projection = naturality.client.ui.menus.previews.MenuPreview.projection();
        for (int births = 0; births < 24 && ACTIVE.size() < target; births++) {
            var offset = new org.joml.Vector3f((RANDOM.nextFloat() * 2 - 1) / projection.m00(),
                    (RANDOM.nextFloat() * 2 - 1) / projection.m11(), -16 - RANDOM.nextFloat() * 32);
            camera.rotation().transform(offset);
            var eye = camera.position();
            var particle = client.particleEngine.createParticle(kind == Biome.Precipitation.SNOW
                    ? NaturalityParticles.SNOW_CLUSTER : NaturalityParticles.RAIN_CLUSTER,
                    eye.x + offset.x, eye.y + offset.y, eye.z + offset.z, 0, 0, 0);
            if (particle instanceof WeatherClusterParticle cluster) ACTIVE.add(cluster);
        }
    }
    public static boolean previewContains(net.minecraft.world.phys.Vec3 position) {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var relative = position.subtract(camera.position());
        var local = new org.joml.Vector3f((float)relative.x, (float)relative.y, (float)relative.z);
        new org.joml.Quaternionf(camera.rotation()).conjugate().transform(local);
        var projection = naturality.client.ui.menus.previews.MenuPreview.projection();
        return Math.abs(local.x * projection.m00()) < 1.15F && Math.abs(local.y * projection.m11()) < 1.15F
                && local.z < -4 && local.z > -80;
    }
    /** The rendered layers' highest top edge; no visible clouds means no spawn ceiling. */
    public static double highestActiveCloudTop(Minecraft client) {
        if (client.options.cloudStatus().get() == CloudStatus.OFF) return Double.POSITIVE_INFINITY;
        var camera = client.gameRenderer.mainCamera();
        var probe = camera.attributeProbe();
        if (probe.getValue(EnvironmentAttributes.CLOUD_COLOR, 0).w() <= 0)
            return Double.POSITIVE_INFINITY;
        float baseHeight = probe.getValue(EnvironmentAttributes.CLOUD_HEIGHT, 0);
        if (!Float.isFinite(baseHeight)) return Double.POSITIVE_INFINITY;
        var clouds = NaturalityConfig.get().clouds;
        if (!clouds.enabled) return baseHeight + (client.options.cloudStatus().get() == CloudStatus.FANCY ? 4 : 0);
        double top = Double.NEGATIVE_INFINITY;
        if (clouds.base.enabled && clouds.base.opacityPercent > 0)
            top = Math.max(top, baseHeight + clouds.base.heightOffset + (clouds.base.style == 2 ? 0 : clouds.base.thickness));
        if (clouds.upper.enabled && clouds.upper.opacityPercent > 0)
            top = Math.max(top, baseHeight + clouds.upper.heightOffset + (clouds.upper.style == 2 ? 0 : clouds.upper.thickness));
        return top == Double.NEGATIVE_INFINITY ? Double.POSITIVE_INFINITY : top;
    }
    /** Conservative whole-card clearance: no chunk loads and no cards straddling a roof. */
    public static double clearance(ClientLevel level, double x, double y, double z, double extent) {
        if(extent==0) {
            int height=WeatherParticleContext.floor(level,(int)Math.floor(x),(int)Math.floor(z));
            return height==Integer.MIN_VALUE?-1:y-Math.max(level.getMinY(),height);
        }
        double floor = level.getMinY();
        for (int bx = (int)Math.floor(x-extent); bx <= (int)Math.floor(x+extent); bx++)
            for (int bz = (int)Math.floor(z-extent); bz <= (int)Math.floor(z+extent); bz++) {
                int height = WeatherParticleContext.floor(level, bx, bz);
                if (height == Integer.MIN_VALUE) return -1;
                floor = Math.max(floor, height);
            }
        return y - extent - floor;
    }

    /** One velocity-aligned card contains many drops/flakes; no per-drop simulation. */
    public static final class WeatherClusterParticle extends SingleQuadParticle {
        private static final ParticleLimit LIMIT = new ParticleLimit(1600);
        private static final Optional<ParticleLimit> OPTIONAL_LIMIT = Optional.of(LIMIT);
        private final boolean snow;
        private final boolean menuPreview;
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
            menuPreview = naturality.client.ui.menus.previews.MenuPreview.active();
            flip = random.nextBoolean();
            double phase=random.nextDouble()*Math.PI*2;
            sway=snow?new WeatherSway(phase):null;

            fallSpeed = snow ? .055 + random.nextDouble() * .035 : .6 + random.nextDouble() * .2;
            quadSize = 1.8F + random.nextFloat() * .5F;
            if (menuPreview) quadSize *= 2;
            lifetime = snow ? 240 : 70;

            hasPhysics = false;
            alpha = 0;
            if (menuPreview) opacity = .5F;
            lastTickTime = level.getGameTime();
            updateVelocity(WeatherParticleContext.state(level),true);
            updateTint();
        }
        public boolean snow() { return snow; }
        public Vec3 position() { return new Vec3(x,y,z); }
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
            return ParticleGeometry.orientation(velocity.x,velocity.y,velocity.z,toCamera.x,toCamera.y,toCamera.z,
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
            double speed = menuPreview ? 4 : naturality.client.systems.sleep.SleepVisuals.speed();
            double nx=x+xd*speed, ny=y+yd*speed, nz=z+zd*speed;
            if (menuPreview) {
                if (!naturality.client.ui.menus.previews.MenuPreview.active() || !ParticleWeather.previewContains(new Vec3(nx,ny,nz))) { remove(); return; }
                setPos(nx,ny,nz);
                updateTint();
                opacity = (snow ? .65F : .5F) * Math.min(1, (lifetime-age)/8F);
                return;
            }
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
            var rotation=ParticleGeometry.orientation(xd,yd,zd,-px,-py,-pz,orientationBasis,orientationRotation);
            alpha=opacity;
            extractRotatedQuad(output,rotation,(float)px,(float)py,(float)pz,partial);
        }
        @Override protected int getLightCoords(float partial) {
            return WeatherParticleContext.light(level,queryPos.set(x,y,z));
        }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
        @Override public Optional<ParticleLimit> getParticleLimit() { return OPTIONAL_LIMIT; }
    }

    /** Three exact-frequency oscillators advanced by a fixed-angle rotation each tick. */
    public static final class WeatherSway {
        private static final double SX=Math.sin(.065),CX=Math.cos(.065);
        private static final double SZ=Math.sin(.065*.81),CZ=Math.cos(.065*.81);
        private static final double SY=Math.sin(.065*.7),CY=Math.cos(.065*.7);
        private double xSin,xCos,zSin,zCos,ySin,yCos;
        public WeatherSway(double phase) {
            xSin=Math.sin(phase);xCos=Math.cos(phase);
            zSin=Math.sin(phase*.81);zCos=Math.cos(phase*.81);
            ySin=Math.sin(phase*.7);yCos=Math.cos(phase*.7);
        }
        public void tick() {
            double next=xSin*CX+xCos*SX;xCos=xCos*CX-xSin*SX;xSin=next;
            next=zSin*CZ+zCos*SZ;zCos=zCos*CZ-zSin*SZ;zSin=next;
            next=ySin*CY+yCos*SY;yCos=yCos*CY-ySin*SY;ySin=next;
        }
        public double x(){return xSin;}
        public double z(){return zCos;}
        public double y(){return ySin;}
    }
}
