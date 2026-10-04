package naturality.test;

import naturality.NaturalityParticles;
import naturality.client.particle.WeatherClusterParticle;
import naturality.client.weather.ParticleWeather;
import naturality.client.weather.WeatherParticleContext;
import naturality.weather.WeatherProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class ParticleWeatherGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class CardProbe extends net.minecraft.client.renderer.state.level.QuadParticleRenderState {
        int color; float left, right, top; org.joml.Quaternionf rotation;
        @Override public void add(net.minecraft.client.particle.SingleQuadParticle.Layer layer,
                float x,float y,float z,float rx,float ry,float rz,float rw,float size,
                float u0,float u1,float v0,float v1,int color,int light) {
            this.color=color; left=u0; right=u1; top=v0; rotation=new org.joml.Quaternionf(rx,ry,rz,rw);
        }
    }
    @Override public void runTest(ClientGameTestContext context) {
        var profile = new WeatherProfile(true);
        profile.overrideRain = profile.overrideWind = profile.overrideTemperature = true;
        profile.rain = 100; profile.wind = 100; profile.temperature = 50;
        var quality = new ParticleStatus[1]; var radius = new int[1]; var oit = new boolean[1];
        context.runOnClient(c -> {
            quality[0]=c.options.particles().get(); radius[0]=c.options.weatherRadius().get(); oit[0]=c.options.improvedTransparency().get();
            c.options.particles().set(ParticleStatus.ALL); c.options.weatherRadius().set(10);
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(s -> naturality.weather.WeatherWorldData.get(s).setProfile("minecraft:overworld", profile));
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            server.runCommand("fill -24 100 -24 24 100 24 grass_block");
            server.runCommand("fill -10 101 12 10 112 12 stone_bricks");
            server.runCommand("tp @a 0 108 -8 0 8");
            // Rain/wind transition from the world's initial automatic state at
            // 0.5 per tick, then allow the particle population to settle.
            context.waitTicks(260);
            world.getConnection().waitForChunksRender();
            context.runOnClient(c -> {
                check(ParticleWeather.radius(c)==15, "Weather reaches 50 percent farther");
                check(ParticleWeather.activeCount()>320 && ParticleWeather.activeCount()<=ParticleWeather.MAX_CLUSTERS, "Downpour is substantially denser but bounded");
                check(ParticleWeather.snowCount()==0, "Warm plains use rain cards");
                checkSharedQueries(c);
                for (boolean snowKind : new boolean[]{false,true}) {
                    var layouts = new java.util.HashSet<String>();
                    boolean flipped=false, unflipped=false;
                    for (int i=0;i<96;i++) {
                        var card=(WeatherClusterParticle)c.particleEngine.createParticle(snowKind ? NaturalityParticles.SNOW_CLUSTER : NaturalityParticles.RAIN_CLUSTER,0,118,0,0,0,0);
                        var probe = new CardProbe();
                        card.extract(probe,c.gameRenderer.mainCamera(),1);
                        var axis = probe.rotation.transform(new Vector3f(0,1,0));
                        var velocity = card.velocity().normalize();
                        check(axis.dot(new Vector3f((float)velocity.x,(float)velocity.y,(float)velocity.z))>.9999, "Both rain and snow preserve sprite Y velocity lock during extraction");
                        int expected = snowKind ? 0xFFFFFF : net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(c.level,new net.minecraft.core.BlockPos(0,118,0));
                        check((probe.color & 0xFFFFFF)==(expected & 0xFFFFFF), "Rain uses biome water color; snow stays white");
                        flipped |= probe.left>probe.right; unflipped |= probe.left<probe.right;
                        layouts.add(Math.min(probe.left,probe.right)+":"+probe.top);
                        card.remove();
                    }
                    check(layouts.size()==(snowKind ? 3 : 6) && flipped && unflipped,
                        "All snow or regular/heavy rain textures and both sprite flip states are used: " + layouts.size());
                }
                try(var renderer=new WeatherEffectRenderer()) {
                    var state=new WeatherRenderState();
                    renderer.extractRenderState(c.level,1,c.gameRenderer.mainCamera().position(),state);
                    check(state.rainColumns.isEmpty() && state.snowColumns.isEmpty() && state.intensity==0,
                        "Vanilla sheets must not double-render over the particles");
                }
                var rain=(WeatherClusterParticle)c.particleEngine.createParticle(NaturalityParticles.RAIN_CLUSTER,0,118,0,0,0,0);
                var snow=(WeatherClusterParticle)c.particleEngine.createParticle(NaturalityParticles.SNOW_CLUSTER,0,118,0,0,0,0);
                check(Math.abs(snow.velocity().y) < Math.abs(rain.velocity().y)*.2, "Snow falls much slower than rain");
                snow.remove();
                double y=rain.getBoundingBox().minY;
                for(int i=0;i<4;i++)rain.tick();
                check(rain.isAlive() && rain.getBoundingBox().minY<y, "Rain moves down in world coordinates");
                var v=rain.velocity();
                check(v.x*v.x+v.z*v.z>0, "Wind changes rain trajectory");
                var rotation=WeatherClusterParticle.orientation(v,new Vec3(2,1,3));
                var axis=rotation.transform(new Vector3f(0,1,0));
                var direction=v.normalize();
                check(axis.dot(new Vector3f((float)direction.x,(float)direction.y,(float)direction.z))>.9999,
                    "Sprite Y stays locked to velocity");
                var toward = new Vec3(2,1,3);
                var projected = toward.subtract(direction.scale(toward.dot(direction))).normalize();
                var normal = rotation.transform(new Vector3f(0,0,1));
                check(normal.dot(new Vector3f((float)projected.x,(float)projected.y,(float)projected.z))>.9999,
                    "Card faces the observer as closely as the velocity lock permits");
                var headOn=WeatherClusterParticle.orientation(v,v);
                var headAxis=headOn.transform(new Vector3f(0,1,0));
                check(headAxis.dot(new Vector3f((float)direction.x,(float)direction.y,(float)direction.z))>.9999,
                    "Head-on view must not break the velocity lock");
                check(Float.isFinite(headOn.x) && Float.isFinite(headOn.w), "Looking along velocity has a finite orientation");
                rain.remove();
            });
            for(boolean improved:new boolean[]{false,true}) {
                context.runOnClient(c->c.options.improvedTransparency().set(improved));
                context.waitTicks(12); context.takeScreenshot("particle-rain-"+improved);
            }
            context.runOnClient(c->c.options.particles().set(ParticleStatus.MINIMAL));
            context.waitTicks(20);
            context.runOnClient(c->check(ParticleWeather.activeCount()>0 && ParticleWeather.activeCount()<=141,
                "Minimal quality retains weather with a reduced budget"));
            context.runOnClient(c->c.options.particles().set(ParticleStatus.ALL));
            context.runOnClient(c->c.reloadResourcePacks());
            context.waitFor(c->c.gui.overlay()==null);
            context.waitTicks(30);
            context.runOnClient(c->check(ParticleWeather.activeCount()>0, "Weather repopulates after a resource reload"));
            profile.temperature=0;
            context.waitTicks(220);
            context.runOnClient(c->{
                check(ParticleWeather.snowCount()>0 && ParticleWeather.snowCount()==ParticleWeather.activeCount(), "Cold plains use only snow cards");
                var snow=(WeatherClusterParticle)c.particleEngine.createParticle(NaturalityParticles.SNOW_CLUSTER,0,118,0,0,0,0);
                double y=snow.getBoundingBox().minY;
                snow.tick(); var first=snow.velocity();
                for(int i=0;i<5;i++)snow.tick();
                check(snow.isAlive() && snow.getBoundingBox().minY<y && !first.equals(snow.velocity()), "Snow falls and changes sway velocity");
                snow.remove();
            });
            for(boolean improved:new boolean[]{false,true}) {
                context.runOnClient(c->c.options.improvedTransparency().set(improved));
                context.waitTicks(12); context.takeScreenshot("particle-snow-"+improved);
            }
            server.runCommand("fill -6 114 -6 6 114 6 stone");
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(c->{
                check(ParticleWeather.clearance(c.level,0,112,0,1.7)<0, "Roof excludes full card footprint");
                var snow=(WeatherClusterParticle)c.particleEngine.createParticle(NaturalityParticles.SNOW_CLUSTER,0,112,0,0,0,0);
                snow.tick(); check(!snow.isAlive(), "Cards cannot remain under roofs");
            });
            server.runCommand("tp @a 0 108 0");
            for(int x:new int[]{-32,0})for(int z:new int[]{-32,0})
                server.runCommand("fillbiome "+x+" 96 "+z+" "+(x+31)+" 127 "+(z+31)+" desert");
            context.waitTicks(25);
            context.runOnClient(c->check(ParticleWeather.activeCount()==0, "Desert stays dry even at temperature zero"));
            profile.rain=0;
            context.waitTicks(210);
            context.runOnClient(c->check(ParticleWeather.activeCount()==0, "Clear weather has no lingering clusters"));
            server.runCommand("execute in minecraft:the_nether run tp @a 0 108 0");
            context.waitTicks(25);
            context.runOnClient(c->check(!ParticleWeather.enabled(c.level) && ParticleWeather.activeCount()==0, "Disabled dimensions have no precipitation particles"));
        } finally {
            context.runOnClient(c->{ParticleWeather.clear();c.options.particles().set(quality[0]);c.options.weatherRadius().set(radius[0]);c.options.improvedTransparency().set(oit[0]);});
        }
    }

    private static void checkSharedQueries(net.minecraft.client.Minecraft client) {
        var level = java.util.Objects.requireNonNull(client.level);
        var config = naturality.config.NaturalityConfig.get().effects;
        boolean enabled = config.weatherParticles;
        WeatherParticleContext.begin(level);
        var first = WeatherParticleContext.state(level);
        try {
            check(first == WeatherParticleContext.state(level), "Cards share one snapshot during a pass");
            var weather = naturality.weather.WeatherSystem.state(level);
            check(weather != null && first.rainWindX() == weather.windX() * .34
                && first.snowWindZ() == weather.windZ() * .10, "Shared wind keeps the original scaling");
            for (int repeat=0;repeat<2;repeat++) {
                for (int x : new int[]{-17,-1,0,16}) for (int y : new int[]{100,118}) {
                    var pos = new net.minecraft.core.BlockPos(x,y,0);
                    check(WeatherParticleContext.precipitation(level,pos) == level.getPrecipitationAt(pos),
                        "Cached precipitation matches the full block position");
                    check(WeatherParticleContext.waterTint(level,pos)
                        == net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level,pos),
                        "Cached tint matches the biome query");
                    check(WeatherParticleContext.floor(level,x,0)
                        == level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,x,0),
                        "Cached floor matches the heightmap across positive and negative columns");
                }
            }
            int distant = 20_000_000;
            check(WeatherParticleContext.floor(level,distant,distant) == Integer.MIN_VALUE,
                "Missing chunks have no cached ground surface");
            check(level.getChunkSource().getChunkNow(distant >> 4,distant >> 4) == null,
                "Weather queries do not load distant chunks");
        } finally {
            WeatherParticleContext.end();
        }
        try {
            config.weatherParticles = false;
            check(!WeatherParticleContext.state(level).enabled(), "Unbatched ticks see setting changes immediately");
            WeatherParticleContext.begin(level);
            check(!WeatherParticleContext.state(level).enabled(), "A new pass does not reuse the previous enabled state");
        } finally {
            WeatherParticleContext.end();
            config.weatherParticles = enabled;
        }
        check(WeatherParticleContext.state(level).enabled(), "Ending a pass releases its disabled snapshot");
    }
}


