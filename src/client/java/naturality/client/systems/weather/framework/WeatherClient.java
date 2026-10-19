package naturality.client.systems.weather.framework;
import naturality.client.systems.weather.wind.WindRendering.WindAudio;

import naturality.client.systems.weather.overworld.lightning.DistantLightningClient;
import naturality.client.systems.weather.overworld.lightning.DistantLightningClient.ThunderAudio;
import naturality.systems.weather.framework.WeatherSystem.WeatherPayload;
import naturality.systems.weather.framework.WeatherSystem;
import naturality.systems.weather.end.framework.EndWeatherSystem.EndWeatherPayload;
import naturality.systems.weather.end.framework.EndWeatherSystem;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import naturality.client.systems.weather.overworld.precipitation.ParticleWeather;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import naturality.config.live.NaturalityConfig;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

@org.jspecify.annotations.NullMarked
public final class WeatherClient {
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static @org.jspecify.annotations.Nullable Object connection;
    private WeatherClient() {}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EndWeatherPayload.TYPE, (p,_) -> EndWeatherSystem.receive(p));
        ClientPlayConnectionEvents.INIT.register((_,_) -> EndWeatherSystem.clearClient());
        ClientPlayConnectionEvents.DISCONNECT.register((_,_) -> EndWeatherSystem.clearClient());
        DistantLightningClient.initialize();
        ThunderAudio.initialize();
        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            WeatherSystem.clearClient();
            WindAudio.stop(client);
            connection = handler;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((_, client) -> {
            WeatherSystem.clearClient();
            WindAudio.stop(client);
            connection = null;
            world = null;
        });
        ClientPlayNetworking.registerGlobalReceiver(WeatherPayload.TYPE, (payload, _) -> WeatherSystem.receive(payload));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(WeatherClient::tick);
    }
    private static void tick(Minecraft client) {
        // The cache is keyed by dimension, so discard it when the play
        // connection changes to a different saved world/server session.
        Object currentConnection = client.getConnection();
        if (connection != currentConnection) {
            WeatherSystem.clearClient();
            WindAudio.stop(client);
            WeatherSoundEnvironment.reset();
            connection = currentConnection;
        }
        if (world != client.level) { WindAudio.stop(client); world = client.level; WeatherSoundEnvironment.reset(); }
        var world = WeatherClient.world;
        if (world == null || client.isPaused()) return;
        WeatherSoundEnvironment.tick(client);
        var state = WeatherSystem.state(world);
        WindAudio.update(client, state);
    }

    /** Local sky access for weather audio, including the open sides of overhangs. */
    public static final class WeatherSoundEnvironment {
        private static final double[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {.70710678, .70710678}, {.70710678, -.70710678},
            {-.70710678, .70710678}, {-.70710678, -.70710678}
        };
        private static final double[] DISTANCES = {2, 4, 8};
        private static float indoor = 1;
        private static boolean initialized;
        private static boolean underground;

        private WeatherSoundEnvironment() {}

        public static void reset() { indoor = 1; initialized = false; underground = false; }
        public static boolean underground() { return underground; }
        public static float indoor() { return indoor; }
        public static float windExposure() { return underground ? 0 : .15F + .85F * (1 - indoor); }
        /** Share the same outdoor test between particle shelter and player ambience. */
        public static boolean hasOpenSkyAccess(Level level, Vec3 origin, Entity context) {
            return openSkyExposure(level, origin, context) > 0;
        }

        private static float openSkyExposure(Level level, Vec3 origin, Entity context) {
            if (level.dimension().equals(Level.NETHER)) {
                return 1; // Nether wind has no indoor or cave shelter checks.
            }
            var probe = new BlockPos.MutableBlockPos().set(origin.x, origin.y, origin.z);
            if (canSeeSky(level,probe)) return 1;
            for (double distance : DISTANCES) {
                float reach = distance <= 2 ? 1 : distance <= 4 ? .85F : .65F;
                for (double[] direction : DIRECTIONS) {
                    double x = origin.x + direction[0] * distance, z = origin.z + direction[1] * distance;
                    probe.set(x, origin.y, z);
                    if (!canSeeSky(level,probe)) continue;
                    // Sheltered samples only need their block coordinates. Build
                    // an exact-position ray endpoint only when the sky is open.
                    Vec3 sample = new Vec3(x, origin.y, z);
                    if (level.clip(new ClipContext(origin, sample, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, context)).getType() == HitResult.Type.MISS)
                        return reach;
                }
            }
            return 0;
        }
        private static boolean canSeeSky(Level level,BlockPos pos) {
            return level instanceof net.minecraft.client.multiplayer.ClientLevel client
                ?WeatherParticleContext.canSeeSky(client,pos):level.canSeeSky(pos);
        }

        public static void tick(Minecraft client) {
            var level = client.level;
            var cameraEntity = client.getCameraEntity();
            if (level == null || cameraEntity == null) { reset(); return; }
            if (level.dimension().equals(Level.NETHER)) {
                indoor = 0; initialized = true; underground = false;
                return;
            }
            Vec3 eye = client.gameRenderer.mainCamera().position();
            underground = naturality.systems.weather.framework.WeatherSystem.WeatherShelter.underground(level, eye, cameraEntity);
            float open = openSkyExposure(level, eye, cameraEntity);
            float target = 1 - open;
            if (!initialized) { indoor = target; initialized = true; }
            else indoor += Math.clamp(target - indoor, -.08F, .08F);
        }
    }

    /** Client-thread scratch data, valid only during a particle update, spawn or render pass. */
    public static final class WeatherParticleContext {
        private static final int MAX_ENTRIES = 8192;
        private static final Long2IntOpenHashMap HEIGHTS = new Long2IntOpenHashMap();
        private static final Long2IntOpenHashMap TINTS = new Long2IntOpenHashMap();
        private static final Long2IntOpenHashMap LIGHTS = new Long2IntOpenHashMap();
        private static final Long2ByteOpenHashMap SKY=new Long2ByteOpenHashMap();
        private static final Long2ObjectOpenHashMap<Biome.Precipitation> PRECIPITATION = new Long2ObjectOpenHashMap<>();
        private static @org.jspecify.annotations.Nullable ClientLevel world;
        private static @org.jspecify.annotations.Nullable State state;
        private static @org.jspecify.annotations.Nullable Wind wind;
        static {
            HEIGHTS.defaultReturnValue(Integer.MAX_VALUE);
            TINTS.defaultReturnValue(Integer.MIN_VALUE);
            LIGHTS.defaultReturnValue(Integer.MIN_VALUE);
        }

        private WeatherParticleContext() { }

        public record State(boolean enabled, double rainWindX, double rainWindZ,
                            double snowWindX, double snowWindZ, float snowFallMultiplier, Vec3 eye, int radius,
                            float heavyFog) {
            public float heavyParticleChance(double x,double y,double z) {
                if(heavyFog==0)return 0;
                double dx=x-eye.x,dy=y-eye.y,dz=z-eye.z;
                double squared=dx*dx+dy*dy+dz*dz;
                return squared>=225?heavyFog:heavyFog*(float)(Math.sqrt(squared)/15);
            }
        }
        public record Wind(boolean enabled,double x,double z,@org.jspecify.annotations.Nullable Entity camera) { }
        public static Wind wind(ClientLevel level) {
            var cached=wind;
            if(world==level && cached!=null)return cached;
            var weather=ParticleWeather.weatherState(level);
            var result=new Wind(weather!=null && NaturalityConfig.get().effects.particleWind,
                weather==null?0:weather.windX()*.007,weather==null?0:weather.windZ()*.007,Minecraft.getInstance().getCameraEntity());
            if(world==level)wind=result;
            return result;
        }

        public static void begin(ClientLevel level) {
            end();
            world = level;
        }

        public static void end() {
            world = null;
            state = null;
            wind = null;
            HEIGHTS.clear();
            TINTS.clear();
            LIGHTS.clear();
            SKY.clear();
            PRECIPITATION.clear();
        }

        public static State state(ClientLevel level) {
            var cached = state;
            if (world == level && cached != null) return cached;
            var client = Minecraft.getInstance();
            var weather = ParticleWeather.weatherState(level);
            // Keep the original double multiplication (weather wind components are floats).
            double windX = weather == null ? 0 : weather.windX();
            double windZ = weather == null ? 0 : weather.windZ();
            var result = new State(client.level == level && NaturalityConfig.get().effects.weatherParticles
                && level.canHaveWeather() && weather != null && weather.rain() > 0,
                windX * .34, windZ * .34, windX * .10, windZ * .10,
                weather == null ? 1 : weather.snowFallMultiplier(),
                client.gameRenderer.mainCamera().position(), ParticleWeather.radius(client),weather==null?0:weather.heavyRainFog());
            if (world == level) state = result;
            return result;
        }

        public static int waterTint(ClientLevel level, BlockPos pos) {
            if (naturality.client.ui.menus.previews.MenuPreview.active()) pos = naturality.client.ui.menus.previews.MenuPreview.spawn();
            if (world != level) return BiomeColors.getAverageWaterColor(level, pos);
            long key = pos.asLong();
            int cached=TINTS.get(key);
            if(cached!=Integer.MIN_VALUE)return cached;
            int color = BiomeColors.getAverageWaterColor(level, pos);
            if (TINTS.size() < MAX_ENTRIES) TINTS.put(key, color);
            return color;
        }

        public static Biome.Precipitation precipitation(ClientLevel level, BlockPos pos) {
            if (naturality.client.ui.menus.previews.MenuPreview.active()) pos = naturality.client.ui.menus.previews.MenuPreview.spawn();
            if (world != level) return level.getPrecipitationAt(pos);
            long key = pos.asLong();
            var cached = PRECIPITATION.get(key);
            if (cached != null) return cached;
            var kind = level.getPrecipitationAt(pos);
            if (PRECIPITATION.size() < MAX_ENTRIES) PRECIPITATION.put(key, kind);
            return kind;
        }

        /** Exact-position light shared for one extraction pass, including dynamic lighting hooks. */
        public static int light(ClientLevel level,BlockPos pos) {
            if (naturality.client.ui.menus.previews.MenuPreview.active()) pos = naturality.client.ui.menus.previews.MenuPreview.spawn().above(8);
            long key=pos.asLong();
            if(world==level) {
                int cached=LIGHTS.get(key);
                if(cached!=Integer.MIN_VALUE)return cached;
            }
            int light=naturality.api.worlds.LoadedChunks.has(level,pos)?LightCoordsUtil.getLightCoords(level,pos):15728640;
            if(world==level && LIGHTS.size()<MAX_ENTRIES)LIGHTS.put(key,light);
            return light;
        }
        public static boolean canSeeSky(ClientLevel level,BlockPos pos) {
            if(world!=level)return level.canSeeSky(pos);
            long key=pos.asLong();
            byte cached=SKY.get(key);
            if(cached!=0)return cached==2;
            boolean visible=level.canSeeSky(pos);
            if(SKY.size()<MAX_ENTRIES)SKY.put(key,(byte)(visible?2:1));
            return visible;
        }

        /** Integer.MIN_VALUE means absent; getChunkNow never loads a chunk. */
        public static int floor(ClientLevel level, int x, int z) {
            long key = ((long)x << 32) ^ (z & 0xffffffffL);
            if(world==level) {
                int cached=HEIGHTS.get(key);
                if(cached!=Integer.MAX_VALUE)return cached;
            }
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            int height = chunk == null ? Integer.MIN_VALUE
                : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,x&15,z&15)+1;
            if (world == level && HEIGHTS.size() < MAX_ENTRIES) HEIGHTS.put(key, height);
            return height;
        }
    }
}
